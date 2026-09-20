#!/usr/bin/env node
'use strict'

const fs = require('fs')

const modulePath = process.env.SFL_PROXY_PROTOCOL_MODULE
if (!modulePath) {
  throw new Error('SFL_PROXY_PROTOCOL_MODULE must point to the pinned minecraft-protocol module')
}

const mc = require(modulePath)
const host = process.argv[2] || '127.0.0.1'
const port = Number(process.argv[3] || '25577')
const username = process.argv[4] || 'SFLProxyBot'
const output = process.argv[5]
const holdMs = Number(process.argv[6] || '12000')

if (!output) {
  throw new Error('Usage: proxy_identity_client.js <host> <port> <username> <output-json> [hold-ms]')
}

let loggedIn = false
let finished = false

const client = mc.createClient({
  host,
  port,
  username,
  auth: 'offline',
  version: '26.2'
})

const deadline = setTimeout(() => {
  if (!finished) {
    console.error('Timed out waiting for Minecraft 26.2 proxy login')
    process.exitCode = 1
    client.end('SFL proxy identity smoke timeout')
  }
}, 30000)

client.once('login', () => {
  loggedIn = true
  const uuid = String(client.uuid || '').toLowerCase()
  if (!uuid) {
    console.error('minecraft-protocol reached login without exposing the client UUID')
    process.exitCode = 1
    client.end('SFL proxy identity smoke missing UUID')
    return
  }

  fs.writeFileSync(output, JSON.stringify({
    username: client.username || username,
    uuid,
    protocol: 776,
    minecraft: '26.2'
  }, null, 2) + '\n', 'utf8')

  console.log('Proxy login reached play state:', username, uuid)
  setTimeout(() => client.end('SFL proxy identity smoke complete'), holdMs)
})

client.on('error', error => {
  console.error(error && error.stack ? error.stack : String(error))
  process.exitCode = 1
})

client.on('end', reason => {
  finished = true
  clearTimeout(deadline)
  if (!loggedIn) {
    console.error('Connection ended before reaching play state:', String(reason || 'unknown'))
    process.exitCode = 1
  }
  setTimeout(() => process.exit(process.exitCode || 0), 50)
})
