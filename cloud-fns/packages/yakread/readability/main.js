let { Readability } = require('@mozilla/readability');
let { JSDOM } = require('jsdom');

function main(opts) {
  let doc = new JSDOM(opts['html'], {url: opts['url']});
  let reader = new Readability(doc.window.document);
  let article = reader.parse();
  return {body: article};
}

exports.main = main;
