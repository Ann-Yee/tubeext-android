"use strict";
const c=document.querySelector("#open-startup");
chrome.storage.local.get({openOnStartup:false},v=>{c.checked=Boolean(v.openOnStartup);});
document.querySelector("#save").addEventListener("click",()=>{
  chrome.storage.local.set({openOnStartup:c.checked},()=>{document.querySelector("#save").textContent="已保存";});
});
