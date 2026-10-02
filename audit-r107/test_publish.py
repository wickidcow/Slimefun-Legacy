"""Offline publisher-boundary tests; fake API responses do not prove a real publication."""
import importlib.util
import json
import os
import tempfile
import unittest
import copy
from pathlib import Path
from unittest.mock import patch
from types import SimpleNamespace

SOURCE = Path(__file__).with_name('publish.py').resolve()

class PublicationGuards(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        previous = os.getcwd()
        os.chdir(self.tmp.name)
        self.addCleanup(os.chdir, previous)
        spec = importlib.util.spec_from_file_location('guarded_publish_test', SOURCE)
        self.m = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.m)
        m = self.m
        self.core, self.old, self.new = b'original core bytes', b'old bundle bytes', b'new validated bundle bytes'
        m.NEW_ZIP_SHA = m.digest(self.new)
        def asset(id, name, data):
            return dict(id=id,name=name,digest='sha256:'+m.digest(data),size=len(data),state='uploaded')
        self.release = dict(id=1,draft=False,prerelease=False,target_commitish=m.CORE_SOURCE,
            body='Old release notes',assets=[asset(10,m.CORE_NAME,self.core),asset(11,m.ZIP_NAME,self.old)])
        self.tag = dict(object=dict(type='commit',sha=m.CORE_SOURCE))
        (m.BACKUP/m.CORE_NAME).write_bytes(self.core)
        (m.BACKUP/m.ZIP_NAME).write_bytes(self.old)
        m.write(m.BACKUP/'release-before.json', self.release)
        m.write(m.BACKUP/'tag-before.json', self.tag)
        (m.NEW/m.ZIP_NAME).write_bytes(self.new)
        m.write(m.EVIDENCE/'prepared.json', dict(core_sha256=m.digest(self.core),old_zip_sha256=m.digest(self.old)))
        self.writes, self.extra = [], None
        for mocked in (patch.dict(os.environ,{'BACKUP_UPLOAD_ID':'12345'}),
                       patch.object(m,'api',self.api),patch.object(m.subprocess,'run',self.command),
                       patch.object(m,'download_release',self.download),patch('builtins.print')):
            mocked.start()
            self.addCleanup(mocked.stop)

    def api(self,path,method='GET',body=None):
        m=self.m
        if path==f'{m.API}/git/ref/tags/v{m.VERSION}': return copy.deepcopy(self.tag)
        if method=='GET' and path==f'{m.API}/releases/tags/v{m.VERSION}': return copy.deepcopy(self.release)
        if path==f'{m.API}/releases/1' and method=='PATCH':
            self.assertEqual(set(body),{'body'})
            self.writes.append(('notes',body['body']))
            self.release['body']=body['body']
            return copy.deepcopy(self.release)
        self.fail((path,method,body))

    def command(self,args,**kwargs):
        m=self.m
        self.assertEqual(args,['gh','release','upload','v'+m.VERSION,str(m.NEW/m.ZIP_NAME),'--repo',m.REPO,'--clobber'])
        self.writes.append(('zip',m.NEW_ZIP_SHA))
        asset=next(a for a in self.release['assets'] if a['name']==m.ZIP_NAME)
        asset.update(id=12,digest='sha256:'+m.NEW_ZIP_SHA,size=len(self.new))
        if self.extra: self.extra()
        return SimpleNamespace(returncode=0)

    def download(self,destination):
        (destination/self.m.CORE_NAME).write_bytes(self.core)
        (destination/self.m.ZIP_NAME).write_bytes(self.new)

    def must_refuse_without_write(self):
        with self.assertRaises((AssertionError,KeyError)): self.m.publish()
        self.assertEqual(self.writes,[])

    def test_success_writes_only_zip_then_notes_and_preserves_core_and_tag(self):
        original,tag=copy.deepcopy(self.release['assets'][0]),copy.deepcopy(self.tag)
        self.m.publish()
        self.assertEqual([x[0] for x in self.writes],['zip','notes'])
        self.assertEqual(self.release['assets'][0],original)
        self.assertEqual(self.tag,tag)
        result=json.loads((self.m.EVIDENCE/'publication-result.json').read_text())
        self.assertTrue(result['core_asset_unchanged'])
        self.assertTrue(result['release_tag_unchanged'])
        self.assertEqual(result['backup_artifact_id'],'12345')

    def test_missing_confirmed_backup_blocks_upload(self):
        os.environ.pop('BACKUP_UPLOAD_ID')
        self.must_refuse_without_write()

    def test_changed_core_id_blocks_upload(self):
        self.release['assets'][0]['id']=55
        self.must_refuse_without_write()

    def test_changed_core_digest_blocks_upload(self):
        self.release['assets'][0]['digest']='sha256:other'
        self.must_refuse_without_write()

    def test_changed_zip_asset_blocks_upload(self):
        self.release['assets'][1]['id']=55
        self.must_refuse_without_write()

    def test_changed_release_notes_block_upload(self):
        self.release['body']='other author edit'
        self.must_refuse_without_write()

    def test_changed_release_id_blocks_upload(self):
        self.release['id']=2
        self.must_refuse_without_write()

    def test_changed_tag_blocks_upload(self):
        self.tag['object']['sha']='other'
        self.must_refuse_without_write()

    def test_prerelease_blocks_upload(self):
        self.release['prerelease']=True
        self.must_refuse_without_write()

    def test_missing_core_blocks_upload(self):
        self.release['assets'].pop(0)
        self.must_refuse_without_write()

    def test_corrupt_candidate_blocks_upload(self):
        (self.m.NEW/self.m.ZIP_NAME).write_bytes(b'corrupt')
        self.must_refuse_without_write()

    def test_other_notes_edit_during_upload_is_not_overwritten(self):
        self.extra=lambda:self.release.update(body='concurrent human notes')
        with self.assertRaises(AssertionError): self.m.publish()
        self.assertEqual([x[0] for x in self.writes],['zip'])
        self.assertEqual(self.release['body'],'concurrent human notes')
        self.assertFalse((self.m.EVIDENCE/'publication-result.json').exists())

    def test_unexpected_uploaded_digest_cannot_get_success_receipt(self):
        self.extra=lambda:self.release['assets'][1].update(digest='sha256:unexpected')
        with self.assertRaises(AssertionError): self.m.publish()
        self.assertEqual([x[0] for x in self.writes],['zip'])
        self.assertFalse((self.m.EVIDENCE/'publication-result.json').exists())

    def test_changed_core_during_upload_cannot_get_notes_or_success(self):
        self.extra=lambda:self.release['assets'][0].update(id=999)
        with self.assertRaises(AssertionError): self.m.publish()
        self.assertEqual([x[0] for x in self.writes],['zip'])

if __name__=='__main__': unittest.main()
