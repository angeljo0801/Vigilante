import test from 'node:test';
import assert from 'node:assert/strict';
import {verifyUrl,challenge} from './guard.mjs';
const lookup=async()=>[{address:'93.184.216.34'}];
test('only approved hosts',async()=>{
  await verifyUrl('https://example.com',['example.com'],lookup);
  await assert.rejects(verifyUrl('https://example.com.evil.test',['example.com'],lookup));
  await assert.rejects(verifyUrl('http://example.com:8080',['example.com'],lookup));
  await assert.rejects(verifyUrl('http://example.com',['example.com'],async()=>[{address:'127.0.0.1'}]));
});
test('access restriction is not data',()=>{
  assert.equal(challenge(403,'',''),true);
  assert.equal(challenge(200,'Just a moment...',''),true);
  assert.equal(challenge(200,'Shop','Products'),false);
});