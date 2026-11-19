"use strict";
function direct(address) { location.assign(address); }
document.querySelector("#go").addEventListener("click",()=>{
  const value=document.querySelector("#search").value.trim();
  if(value) direct("https://m.youtube.com/results?search_query="+encodeURIComponent(value));
});
document.querySelector("#search").addEventListener("keydown",e=>{
  if(e.key==="Enter")document.querySelector("#go").click();
});
document.querySelector("#market").addEventListener("click",()=>{
  chrome.tabs.create({url:"https://chromewebstore.google.com/category/extensions"});
});
document.querySelector("#extensions").addEventListener("click",()=>{
  chrome.tabs.create({url:"chrome://extensions/"});
});
