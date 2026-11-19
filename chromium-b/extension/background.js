"use strict";
// Only first installation opens a YouTube tab; later startup respects user choice.
chrome.runtime.onInstalled.addListener(details=>{
  if(details.reason==="install") chrome.tabs.create({url:"https://m.youtube.com/"});
});
chrome.runtime.onStartup.addListener(()=>{
  chrome.storage.local.get({openOnStartup:false},result=>{
    if(result.openOnStartup)chrome.tabs.create({url:"https://m.youtube.com/"});
  });
});
