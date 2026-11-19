// SVG 장면을 JPG로 렌더링해서 앱 assets 폴더에 저장한다.
const path = require('path');
const { chromium } = require('playwright');
const { svg, names, W, H } = require('./scenes');

(async () => {
  const out = path.resolve(__dirname, '../../app/src/main/assets/images');
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  for (const name of process.argv.slice(2).length ? process.argv.slice(2) : names) {
    await page.setContent(`<html><body style="margin:0">${svg(name)}</body></html>`);
    await page.screenshot({ path: path.join(out, `${name}.jpg`), type: 'jpeg', quality: 82 });
    console.log('rendered', name);
  }
  await browser.close();
})();
