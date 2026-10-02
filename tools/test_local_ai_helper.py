import json
import unittest
from local_ai_helper import validate_proxy_request

class LocalAiHelperTests(unittest.TestCase):
    def setUp(self):
        self.headers = {'Host':'127.0.0.1:8765', 'Origin':'http://127.0.0.1:8765', 'Content-Type':'application/json'}
        self.body = {'model':'local:small','messages':[{'role':'user','content':'こんにちは'}], 'stream':False, 'keep_alive':'0',
                     'options':{'num_predict':400,'num_ctx':4096,'temperature':0.3},
                     'format':{'type':'object','additionalProperties':False,'properties':{'reply':{'type':'string'},'suggestion':{'type':'string'}},'required':['reply','suggestion']}}
    def send(self, path='/ollama/api/chat', headers=None, body=None):
        return validate_proxy_request('POST',path,headers or self.headers,json.dumps(self.body if body is None else body).encode(),8765)
    def test_fixed_routes_and_bounded_json(self):
        self.assertEqual('/api/chat',self.send()[0])
        self.assertEqual(('/api/tags',None),validate_proxy_request('GET','/ollama/api/tags',self.headers,b'',8765))
    def test_no_foreign_origin_host_or_arbitrary_relay(self):
        for headers in [self.headers | {'Origin':'https://evil.test'},self.headers | {'Host':'evil.test:8765'},self.headers | {'Origin':''}]:
            with self.assertRaises(ValueError): self.send(headers=headers)
        for path in ['/ollama/api/pull','http://evil.test','/ollama/api/chat?url=http://evil.test','/ollama/../secret']:
            with self.assertRaises(ValueError): self.send(path=path)
    def test_cloud_oversized_context_and_extra_options_rejected(self):
        for change in [{'model':'model:cloud'},{'stream':True},{'messages':self.body['messages']*14},{'tools':[]},{'options':{'num_predict':999999,'num_ctx':4096,'temperature':0.3}}]:
            with self.assertRaises(ValueError): self.send(body=self.body|change)
