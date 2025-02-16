let juice = require('juice');

function main(opts) {
  let webResources = {
    images: false,
    scripts: false,
    svgs: false,
    //links: false,
  };
  let html = juice(opts['html'], { webResources: webResources });
  return { body: { html } };
}

exports.main = main;
