"""Generate structured data from the current public release and visible landing."""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent / 'public'
BASE = 'https://mando.sherlockhomeless.net/'

def update():
    path = ROOT / 'index.html'
    html = path.read_text()
    releases = json.loads((ROOT / 'release.json').read_text())
    apk = next(item for item in releases if item['file'].endswith('.apk'))
    match = re.fullmatch(r'mando-(\d+\.\d+\.\d+-alpha\.\d+)\.apk', apk['file'])
    if not match or 'downloads/' + apk['file'] not in html:
        raise ValueError('Release and visible APK link must agree')
    if (ROOT / 'downloads' / apk['file']).stat().st_size != apk['bytes']:
        raise ValueError('Release size mismatch')
    description = 'Controla Windows desde Android: touchpad, teclado, volumen y multimedia. Descarga Mando y su servidor para Windows.'
    data = {'@context':'https://schema.org', '@graph':[
        {'@type':'WebSite', '@id':BASE+'#website', 'url':BASE, 'name':'Mando', 'inLanguage':'es'},
        {'@type':'WebPage', '@id':BASE+'#webpage', 'url':BASE,
         'name':'Mando · Tu PC, desde el móvil', 'description':description,
         'inLanguage':'es', 'isPartOf':{'@id':BASE+'#website'},
         'mainEntity':{'@id':BASE+'#android-app'}},
        {'@type':'SoftwareApplication', '@id':BASE+'#android-app', 'name':'Mando',
         'url':BASE, 'description':description, 'applicationCategory':'UtilitiesApplication',
         'operatingSystem':'Android 9 o posterior', 'softwareVersion':match[1],
         'downloadUrl':BASE+'downloads/'+apk['file'],
         'image':BASE+'assets/icon.svg', 'screenshot':BASE+'assets/control-demo.png',
         'softwareRequirements':'Mando Server en Windows 11 y acceso a la misma red local',
         'mainEntityOfPage':{'@id':BASE+'#webpage'}}
    ]}
    block = '<script id="structured-data" type="application/ld+json">\n' + json.dumps(data, ensure_ascii=False, indent=2).replace('<', '\\u003c') + '\n</script>'
    html = re.sub(r'<script id="structured-data" type="application/ld\+json">.*?</script>\s*', '', html, flags=re.S)
    if '<meta property="og:site_name"' not in html:
        html = html.replace('</head>', '<meta property="og:site_name" content="Mando"></head>')
    path.write_text(html.replace('</head>', block+'\n</head>'))
    return data

if __name__ == '__main__':
    update()
    print('Schema updated from the published release; no ratings or offers invented.')
