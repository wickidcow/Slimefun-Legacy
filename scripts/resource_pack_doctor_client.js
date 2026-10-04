#!/usr/bin/env node
'use strict'

const readline = require('node:readline')
const [modulePath, portText] = process.argv.slice(2)
if (!modulePath || !/^\d+$/.test(portText || '')) {
  throw new Error('Usage: resource_pack_doctor_client.js <pinned-protocol-module> <loopback-port>')
}
const version = require(modulePath + '/package.json').version
if (version !== '1.66.2+complexity.26.2.3') throw new Error('Unexpected protocol fixture version: ' + version)
const client = require(modulePath).createClient({
  host: '127.0.0.1', port: Number(portText), username: 'SFLDoctorBot', auth: 'offline', version: '26.2'
})
let joined = false
let requestedQuit = false
let position = { x: 0, y: 0, z: 0, yaw: 0, pitch: 0 }
const deadline = setTimeout(() => fail(new Error('Connected-player probe exceeded 120 seconds')), 120000)
const ticks = setInterval(() => {
  if (client.state === 'play') client.write('tick_end', {})
}, 50)

function fail (error) {
  console.error(error.stack || String(error))
  process.exitCode = 1
  client.end('Doctor client failed')
}

client.on('login', () => {
  joined = true
  console.log('Doctor client reached play:', client.username, client.uuid)
})
client.on('position', packet => {
  for (const key of ['x', 'y', 'z', 'yaw', 'pitch']) {
    position[key] = packet[key] + (packet.flags[key] ? position[key] : 0)
  }
  client.write('teleport_confirm', { teleportId: packet.teleportId })
  client.write('position_look', { ...position, flags: { onGround: false, hasHorizontalCollision: false } })
})
client.on('error', fail)
client.on('disconnect', packet => console.error('Server disconnect:', JSON.stringify(packet)))
client.on('end', reason => {
  clearTimeout(deadline)
  clearInterval(ticks)
  if (!joined || !requestedQuit) {
    console.error('Unexpected client disconnect:', reason)
    process.exitCode = 1
  }
  process.exit(process.exitCode || 0)
})
readline.createInterface({ input: process.stdin }).on('line', line => {
  if (line !== 'quit') return fail(new Error('Unexpected client control input'))
  requestedQuit = true
  client.end('Doctor client complete')
})
