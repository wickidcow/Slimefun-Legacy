from pathlib import Path
import runpy

runpy.run_path('audit-release/prepare_4_1_65.py', run_name='__main__')
path = Path('README.md')
text = path.read_text()
replacements = {
    'Slimefun Legacy 4.1.64 is tested primarily': 'Slimefun Legacy 4.1.65 is tested primarily',
    'The previous stable regression baseline is 4.1.63.': 'The previous stable regression baseline is 4.1.64.',
    'Slimefun Legacy 4.1.61 uses the exact released 4.1.60 source commit as its release-blocking compatibility baseline. Required addons that work against 4.1.60 but regress against the 4.1.61 candidate block release.':
        'Slimefun Legacy 4.1.65 uses the exact released 4.1.64 source commit as its release-blocking compatibility baseline. Required addons that work against 4.1.64 but regress against the 4.1.65 candidate block release.',
}
for old, new in replacements.items():
    assert text.count(old) == 1, old
    text = text.replace(old, new)
path.write_text(text)
print('Current README version and previous-stable descriptions synchronized; historical changelog remains unchanged.')
