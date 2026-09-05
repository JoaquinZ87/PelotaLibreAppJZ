package com.jz.pelotalibretv

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.jz.pelotalibretv.data.ConfigCodec
import com.jz.pelotalibretv.data.RecipeEngine
import com.jz.pelotalibretv.data.RemoteConfig
import com.jz.pelotalibretv.data.SignedConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RecipeInspectorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch(Dispatchers.IO) {
            val report = JSONObject()
            try {
                RemoteConfig.init(applicationContext)
                fun input(name: String): File {
                    require(name.matches(Regex("[a-zA-Z0-9_.-]+")) && !name.contains(".."))
                    return File(getExternalFilesDir(null), name)
                }
                val configName = intent.getStringExtra("config")
                val text = configName?.let { input(it).readText() } ?: assets.open("config-v2.json").bufferedReader().use { it.readText() }
                val snapshot = ConfigCodec.decode(text)
                report.put("validSources", snapshot.sources.size)
                intent.getStringExtra("manifest")?.let { name ->
                    val key = assets.open("config-public-key.txt").bufferedReader().use { it.readText().trim() }
                    report.put("signedRevision", SignedConfig.verify(input(name).readText(), key).getLong("revision"))
                }
                val sourceId = intent.getStringExtra("sourceId")
                val selected = snapshot.sources.filter { sourceId == "all" || it.id == sourceId }
                val results = JSONArray()
                for (source in selected) {
                    val result = JSONObject().put("source", source.id)
                    runCatching {
                        val recipe = JSONObject(source.agendaRecipe ?: error("Sin receta"))
                        val sample = intent.getStringExtra("sample")
                        val page = sample?.let { com.jz.pelotalibretv.data.SiteHttp.Page(input(it).readText(), source.mirrors.first() + source.agendaPath) }
                            ?: RecipeEngine.fetch(source, recipe, source.agendaPath, source.mirrors.first())
                        val events = RecipeEngine.parse(page.body, page.url, source, recipe)
                        result.put("events", events.size)
                        result.put("signals", events.sumOf { it.servers.size })
                        result.put("firstTitle", events.firstOrNull()?.title.orEmpty())
                        result.put("firstTime", events.firstOrNull()?.time.orEmpty())
                        result.put("firstDate", events.firstOrNull()?.date.orEmpty())
                    }.onFailure { result.put("error", it.message) }
                    results.put(result)
                }
                report.put("results", results)
            } catch (error: Exception) {
                report.put("error", error.message)
            }
            File(getExternalFilesDir(null), "recipe-report.json").writeText(report.toString(2))
            Log.i("RecipeInspector", report.toString())
            runOnUiThread { finish() }
        }
    }
}
