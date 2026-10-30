'use strict'
const mineflayer = require('mineflayer')
const { Vec3 } = require('vec3')
const bot = mineflayer.createBot({ host: '127.0.0.1', port: Number(process.argv[2] || 25595),
  username: 'BDWProbe', version: process.argv[3] || '1.20.2', auth: 'offline' })
let mining
bot.on('messagestr', async text => {
  console.log(text)
  const target = /BDW_TARGET (\d+)/.exec(text)
  if (target) {
    const entity = bot.entities[Number(target[1])]
    if (entity) { await bot.lookAt(entity.position.offset(0, 0.5, 0), true); bot.activateEntity(entity) }
  }
  const block = /BDW_BLOCK (-?\d+) (-?\d+) (-?\d+)/.exec(text)
  if (block) {
    if (mining) clearInterval(mining)
    const pos = new Vec3(Number(block[1]), Number(block[2]), Number(block[3]))
    await bot.lookAt(pos.offset(0.5, 0.5, 0.5), true)
    mining = setInterval(() => {
      const targetBlock = bot.blockAt(pos)
      if (targetBlock && targetBlock.name !== 'air') bot.activateBlock(targetBlock).catch(console.error)
      else { clearInterval(mining); mining = null }
    }, 200)
  }
  if (text.includes('BDW_PROBE_DONE')) { if (mining) clearInterval(mining); bot.quit(); setTimeout(() => process.exit(0), 200) }
})
bot.on('error', error => { console.error(error); process.exitCode = 1 })
bot.on('kicked', reason => console.error('kicked', reason))
setTimeout(() => { if (mining) clearInterval(mining); bot.quit(); process.exit(2) }, 120000).unref()
