function $(selector) {
  return document.querySelector(selector);
}

function renderPost() {
  let element = $("#post-content");
  let html = element.getAttribute('data-contents');
  let options = {
    USE_PROFILES: {
      html: true,
      svg: true,
    },
    ADD_ATTR: ["target"],
    FORCE_BODY: true,
  };
  element.innerHTML = DOMPurify.sanitize(html, options);
  updateMediaQueries();
  scopePostCSS();
}

function updateMediaQueries() {
  let container = $("#post-content");
  let offset = window.innerWidth - container.offsetWidth;
  Array.from(document.styleSheets)
    .filter(sheet => container.contains(sheet.ownerNode))
    .flatMap(sheet => Array.from(sheet.cssRules))
    .filter(rule => !!rule.media)
    .forEach(rule => {
      rule.media.mediaText = rule.media.mediaText.replace(
        /-width: (\d+)px/, (_, width) => `-width: ${parseInt(width) + offset}px`
      );
    });
}

function scopeRule(rule) {
  if (!!rule.selectorText) {
    rule.selectorText = "#post-content " + rule.selectorText;
  }
  if (!!rule.cssRules) {
    Array.from(rule.cssRules).forEach(scopeRule);
  }
}

function scopePostCSS() {
  let container = $("#post-content");
  Array.from(document.styleSheets)
    .filter(sheet => container.contains(sheet.ownerNode))
    .flatMap(sheet => Array.from(sheet.cssRules))
    .forEach(scopeRule);
}
