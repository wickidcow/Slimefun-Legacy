#!/usr/bin/env python3
"""Exercise exact core dependency normalization; no addon or server behavior is mocked."""
from pathlib import Path
import shutil
import tempfile
import unittest
import xml.etree.ElementTree as ET

import compare_addon_slimefun_compatibility as wrapper
import compare_addon_slimefun_compatibility_base as base


class CanonicalCoreDependencyTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def pom(self, group='com.github.wickidcow', artifact='Slimefun-Legacy', properties='', extra=''):
        text = f'''<project xmlns="http://maven.apache.org/POM/4.0.0">
<properties>{properties}</properties><dependencies>
<dependency><groupId>{group}</groupId><artifactId>{artifact}</artifactId><version>4.1.60</version>
<scope>provided</scope><optional>true</optional><exclusions><exclusion><groupId>old</groupId><artifactId>api</artifactId></exclusion></exclusions></dependency>
{extra}</dependencies></project>'''
        (self.root / 'pom.xml').write_text(text)
        return text.encode()

    def records(self):
        root = ET.parse(self.root / 'pom.xml').getroot()
        return [dict((wrapper.local_name(child.tag), child.text) for child in dep)
                for dep in root.iter() if wrapper.local_name(dep.tag) == 'dependency']

    def test_exact_literal_legacy_coordinate_is_replaced(self):
        self.pom()
        self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
        row = self.records()[0]
        self.assertEqual(('com.github.slimefun', 'Slimefun', 'Exact-CI'),
                         (row['groupId'], row['artifactId'], row['version']))
        self.assertEqual('provided', row['scope'])
        self.assertEqual('true', row['optional'])
        self.assertIn('exclusions', row)

    def test_property_based_canonical_coordinates_are_resolved(self):
        self.pom('${sf.group}', '${sf.artifact}',
                 '<sf.group>com.github.wickidcow</sf.group><sf.artifact>Slimefun-Legacy</sf.artifact>')
        self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
        self.assertEqual('Exact-CI', self.records()[0]['version'])

    def test_chained_properties_and_whitespace_remain_supported(self):
        self.pom('${sf.group}', '${sf.artifact}',
                 '<sf.group>${owner}</sf.group><owner> com.github.wickidcow </owner><sf.artifact> Slimefun-Legacy </sf.artifact>')
        self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
        self.assertEqual('Slimefun', self.records()[0]['artifactId'])

    def test_existing_core_families_are_unchanged(self):
        for group in ['com.github.Slimefun', 'com.github.SlimefunGuguProject', 'com.github.Slimefun-United', 'com.github.StarWishsama']:
            with self.subTest(group=group):
                self.pom(group, 'Slimefun4')
                self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
                self.assertEqual('Exact-CI', self.records()[0]['version'])

    def test_nearby_names_do_not_impersonate_the_canonical_core(self):
        for group, artifact in [('com.github.wickidcow.extra', 'Slimefun-Legacy'),
                                ('com.github.other', 'Slimefun-Legacy'),
                                ('com.github.wickidcow', 'Slimefun-Legacy-Guide'),
                                ('com.github.wickidcow', 'SF_SlimefunLegacy'),
                                ('com.github.wickidcow', 'Slimefun4')]:
            with self.subTest(group=group, artifact=artifact):
                before = self.pom(group, artifact)
                self.assertFalse(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
                self.assertEqual(before, (self.root / 'pom.xml').read_bytes())

    def test_unrelated_dependencies_remain_unchanged(self):
        self.pom(extra='<dependency><groupId>org.example</groupId><artifactId>addon</artifactId><version>7.0</version><scope>test</scope></dependency>')
        self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
        self.assertEqual(dict(groupId='org.example', artifactId='addon', version='7.0', scope='test'), self.records()[1])

    def test_unresolved_or_cyclic_properties_are_not_guessed(self):
        for properties in ['', '<sf.group>${other}</sf.group><other>${sf.group}</other>']:
            before = self.pom('${sf.group}', 'Slimefun-Legacy', properties)
            self.assertFalse(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))
            self.assertEqual(before, (self.root / 'pom.xml').read_bytes())

    def test_case_normalization_matches_the_existing_comparator_contract(self):
        self.pom('COM.GITHUB.WICKIDCOW', 'SLIMEFUN-LEGACY')
        self.assertTrue(wrapper.patch_maven_dependency(self.root, 'Exact-CI'))

    def test_copy_instrumentation_does_not_modify_original_source(self):
        before = self.pom()
        copy = self.root / 'disposable'
        copy.mkdir()
        shutil.copy2(self.root / 'pom.xml', copy / 'pom.xml')
        self.assertTrue(wrapper.patch_maven_dependency(copy, 'Exact-CI'))
        self.assertEqual(before, (self.root / 'pom.xml').read_bytes())

    def test_gradle_script_has_the_exact_legacy_pair_without_widening_addon_names(self):
        script = base.write_gradle_init_script(self.root).read_text()
        self.assertIn("group == 'com.github.wickidcow' && artifact == 'slimefun-legacy'", script)
        self.assertIn('return canonicalLegacy || (coreArtifact && coreGroup)', script)
        self.assertNotIn("artifact.contains('slimefun')", script)
        self.assertIn('SLIMEFUN_COMPATIBILITY_JAR', script)


if __name__ == '__main__':
    unittest.main(argv=[__file__])
