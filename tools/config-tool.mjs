import { readFile, writeFile, mkdir, access } from 'node:fs/promises';
import { resolve, dirname, join, relative, isAbsolute } from 'node:path';
import { createHash, createPrivateKey, createPublicKey, sign, verify } from 'node:crypto';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const args = process.argv.slice(2);
const command = args[0] || 'validate';
const option = name => args[args.indexOf(name) + 1];
const catalogPath = args.includes('--catalog') ? resolve(option('--catalog')) : join(root, 'config/catalog.json');
const pathPattern = /^\$(?:\.[A-Za-z_][A-Za-z0-9_-]*(?:\[\*\])?)*$/;
const operations = new Set(['trim', 'query', 'base64', 'urlDecode', 'absolute', 'before', 'after', 'replace', 'default']);
const assert = (condition, message) => { if (!condition) throw new Error(message); };

function validateRecipe(recipe, resolver = false) {
    assert(['html', 'json'].includes(recipe.format), 'Formato desconocido');
    assert(['HH:mm', 'HH:mm:ss', 'h:mma', 'h:mm a'].includes(recipe.timeFormat || 'HH:mm'), 'Formato de hora desconocido');
    const selection = value => {
        assert(typeof value === 'string' && value.length > 0 && value.length <= 500, 'Selector inválido');
        if (recipe.format === 'json') assert(pathPattern.test(value), `Ruta JSON no soportada: ${value}`);
    };
    const field = spec => {
        assert(spec && typeof spec === 'object', 'Campo inválido');
        if (spec.select !== undefined) selection(spec.select);
        assert(['text', 'ownText', 'attr'].includes(spec.read || 'text'), 'Lectura desconocida');
        if (spec.read === 'attr') assert(typeof spec.attribute === 'string' && spec.attribute.length, 'Falta atributo');
        assert(Array.isArray(spec.transforms || []) && (spec.transforms || []).length <= 12, 'Demasiadas transformaciones');
        for (const step of spec.transforms || []) {
            assert(operations.has(step.op), `Operación desconocida: ${step.op}`);
            if (['query', 'before', 'after', 'replace', 'default'].includes(step.op)) assert(typeof step.value === 'string', 'Falta value');
            if (['before', 'after', 'replace'].includes(step.op)) assert(step.value.length, 'Separador vacío');
        }
    };
    if (resolver) field(recipe.url);
    else {
        selection(recipe.items);
        selection(recipe.recognize);
        assert(recipe.fields?.title, 'Falta título');
        Object.values(recipe.fields).forEach(field);
        if (recipe.servers) {
            selection(recipe.servers.items);
            assert(recipe.servers.fields?.url, 'Falta URL de señal');
            Object.values(recipe.servers.fields).forEach(field);
        }
    }
    const follow = recipe.request?.follow || [];
    assert(Array.isArray(follow) && follow.length <= 3, 'Demasiados pasos');
    follow.forEach(step => validateRecipe({ format: step.format, url: step.url }, true));
}

async function load() {
    const catalog = JSON.parse(await readFile(catalogPath, 'utf8'));
    assert(catalog.schemaVersion === 2 && catalog.minEngineVersion === 1, 'Esquema/motor incompatible');
    const base = dirname(catalogPath);
    const local = path => {
        const absolute = resolve(base, path);
        const rel = relative(base, absolute);
        assert(!rel.startsWith('..') && !isAbsolute(rel), 'Archivo fuera del catálogo');
        return absolute;
    };
    const recipes = {};
    for (const path of catalog.recipeFiles) {
        const id = path.split('/').at(-1).replace(/\.json$/, '');
        assert(!recipes[id], 'Receta duplicada');
        recipes[id] = JSON.parse(await readFile(local(path), 'utf8'));
        validateRecipe(recipes[id], !!recipes[id].url);
    }
    const sources = [];
    const ids = new Set();
    for (const path of catalog.sourceFiles) {
        const source = JSON.parse(await readFile(local(path), 'utf8'));
        assert(/^[a-zA-Z0-9_-]{1,80}$/.test(source.id) && !ids.has(source.id), 'ID inválido o duplicado');
        ids.add(source.id);
        assert(source.name && source.mirrors?.length > 0 && source.mirrors.length <= 10, 'Fuente incompleta');
        source.mirrors.forEach(mirror => {
            const url = new URL(mirror);
            assert(['https:', 'http:'].includes(url.protocol) && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash, 'Mirror inválido');
        });
        for (const key of ['agendaRecipe', 'channelRecipe', 'resolverRecipe']) {
            if (!source[key]) continue;
            const recipe = typeof source[key] === 'string' ? recipes[source[key]] : source[key];
            validateRecipe(recipe, key === 'resolverRecipe');
        }
        assert(source.agendaRecipe, 'Falta receta de agenda');
        sources.push(source);
    }
    assert(sources.length > 0 && sources.length <= 100, 'Cantidad de fuentes inválida');
    const { sourceFiles, recipeFiles, ...settings } = catalog;
    return { ...settings, recipes, sources };
}

const config = await load();
if (command === 'validate') {
    console.log(`OK: ${config.sources.length} fuentes, ${Object.keys(config.recipes).length} recetas. Validación estructural; no demuestra extracción ni reproducción.`);
} else if (command === 'bundle') {
    assert(args.includes('--out') && args.includes('--key') && args.includes('--revision'), 'Usar bundle --out DIR --key PRIVATE.pem --revision NUMERO');
    const out = resolve(option('--out'));
    const revision = Number(option('--revision'));
    assert(Number.isSafeInteger(revision) && revision > 0, 'Revisión inválida');
    const key = createPrivateKey(await readFile(resolve(option('--key'))));
    const pinned = (await readFile(join(root, 'app/src/main/assets/config-public-key.txt'), 'utf8')).trim();
    assert(createPublicKey(key).export({ type: 'spki', format: 'der' }).toString('base64') === pinned, 'La clave no corresponde al APK');
    const bundleDir = join(out, 'bundles', String(revision));
    let exists = true;
    try { await access(bundleDir); } catch { exists = false; }
    assert(!exists, 'Revisión existente: no se sobreescribe');
    await mkdir(bundleDir, { recursive: true });
    const sourceFiles = [];
    for (const source of config.sources) {
        const materialized = { ...source };
        for (const key of ['agendaRecipe', 'channelRecipe', 'resolverRecipe']) {
            if (typeof source[key] === 'string') materialized[key] = config.recipes[source[key]];
        }
        const bytes = Buffer.from(JSON.stringify(materialized, null, 2) + '\n');
        assert(bytes.length <= 100000, 'Fuente demasiado grande');
        const path = `bundles/${revision}/${source.id}.json`;
        await writeFile(join(out, path), bytes, { flag: 'wx' });
        sourceFiles.push({ path, sha256: createHash('sha256').update(bytes).digest('hex') });
    }
    const { recipes, sources, ...settings } = config;
    const payload = Buffer.from(JSON.stringify({ ...settings, revision, sourceFiles }));
    const signature = sign('RSA-SHA256', payload, key);
    assert(verify('RSA-SHA256', payload, createPublicKey(key), signature), 'Verificación de firma falló');
    await writeFile(join(out, 'manifest.json'), JSON.stringify({ payload: payload.toString('base64'), signature: signature.toString('base64') }, null, 2) + '\n');
    await writeFile(join(out, 'config-v2.json'), JSON.stringify({ ...config, revision }, null, 2) + '\n');
    console.log(`Bundle firmado ${revision}: ${out}. Subir bundles primero y manifest.json al final; no se publicó nada automáticamente.`);
} else {
    throw new Error(`Comando desconocido: ${command}`);
}
