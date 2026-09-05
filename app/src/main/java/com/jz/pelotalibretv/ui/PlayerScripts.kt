package com.jz.pelotalibretv.ui

import org.json.JSONArray

fun playerScript(adText: List<String>): String = """
(function(){
  if(window.__pelotaPlayerInstalled)return;
  window.__pelotaPlayerInstalled=true;
  var adText=${JSONArray(adText)};
  var started=new WeakSet();
  function update(){
    if(document.hidden)return;
    var videos=document.querySelectorAll('video');
    for(var index=0;index<videos.length;index++){
      var video=videos[index];
      if(video.offsetWidth<120 || video.offsetHeight<70)continue;
      if(!video.paused && video.readyState>=2){started.add(video);continue;}
      if(started.has(video) || video.ended)continue;
      try{
        video.muted=false;
        video.volume=1;
        var promise=video.play();
        if(promise && promise.catch)promise.catch(function(){});
      }catch(error){}
    }
    if(!adText.length)return;
    var elements=document.querySelectorAll('div,section,aside,dialog');
    for(var index=elements.length-1;index>=0;index--){
      var element=elements[index];
      if(element.querySelector('video,iframe') || !element.offsetWidth)continue;
      var text=(element.textContent||'').trim().toLowerCase();
      if(text.length>700)continue;
      for(var word=0;word<adText.length;word++){
        if(text.indexOf(adText[word].toLowerCase())>=0){
          element.style.setProperty('display','none','important');
          break;
        }
      }
    }
  }
  update();
  document.addEventListener('DOMContentLoaded',update,{once:true});
  var timer=setInterval(update,1500);
  window.addEventListener('pagehide',function(){clearInterval(timer);});
  window.addEventListener('pageshow',function(event){if(event.persisted){timer=setInterval(update,1500);update();}});
})();
""".trimIndent()
