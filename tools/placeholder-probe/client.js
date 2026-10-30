'use strict'
// Reuse the separately locked acceptance-fixture dependencies; no production code.
const mineflayer = require('../runtime-probe/node_modules/mineflayer')
const options = { host: '127.0.0.1', port: Number(process.argv[2] || 25593), version: '1.20.2', auth: 'offline' }
let owner, ownerId, observer, swingCount = 0, done = false, reconnecting = false
function connectOwner () {
  owner = mineflayer.createBot({ ...options, username: 'BDWPlaceholder' })
  owner.on('spawn', () => { ownerId = owner.entity.id })
  owner.on('messagestr', async text => {
    console.log(text)
    try {
      const villager = /^PH_VILLAGER (\d+)$/.exec(text)
      if (villager) {
        const entity = owner.entities[Number(villager[1])]
        if (entity) { await owner.lookAt(entity.position.offset(0, 1.4, 0), true); owner.activateEntity(entity) }
      }
      if (text === 'PH_AIR') {
        swingCount = 0
        owner._client.write('use_item', { hand: 0, sequence: 1 })
        setTimeout(() => owner.chat('/phprobe swing ' + swingCount), 700)
      }
      if (text === 'PH_CLICK') await owner.clickWindow(36, 0, 0)
      if (text === 'PH_CLOSE') owner._client.write('close_window', { windowId: 0 })
      if (text === 'PH_DROP' || text === 'PH_SWAP') owner._client.write('block_dig', {
        status: text === 'PH_DROP' ? 4 : 6, location: { x: 0, y: 0, z: 0 }, face: 0, sequence: 2
      })
      if (text === 'PH_RECONNECT') {
        reconnecting = true
        owner.quit()
        setTimeout(() => { reconnecting = false; connectOwner() }, 1500)
      }
      if (text.startsWith('PH_DONE')) {
        done = true
        owner.quit(); observer.quit()
        setTimeout(() => process.exit(text.includes('failed=0') ? 0 : 1), 300)
      }
    } catch (error) { console.error('client action:', error.message) }
  })
  owner.on('death', () => setTimeout(() => owner.respawn(), 750))
  owner.on('kicked', reason => { if (!done) console.error('owner kicked', reason) })
  owner.on('error', error => { if (!reconnecting && !done) console.error('owner:', error.message) })
}
observer = mineflayer.createBot({ ...options, username: 'BDWObserver' })
observer._client.on('animation', packet => {
  if (packet.entityId === ownerId && packet.animation === 3) { swingCount++; console.log('PH_OBSERVED_OFFHAND_SWING') }
})
observer.on('spawn', () => { if (!owner) setTimeout(connectOwner, 500) })
observer.on('error', error => console.error('observer:', error.message))
setTimeout(() => {
  if (!done) { console.error('placeholder probe timed out'); if (owner) owner.quit(); observer.quit(); process.exit(2) }
}, 180000).unref()
