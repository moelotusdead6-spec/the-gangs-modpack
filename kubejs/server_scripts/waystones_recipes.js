ServerEvents.recipes(event => {
    event.remove({ mod: 'waystones' })

    event.shaped('waystones:warp_stone', [
        'DED',
        'EGE',
        'DED'
    ], {
        D: 'minecraft:amethyst_shard',
        E: 'minecraft:ender_pearl',
        G: '#balm:emeralds'
    }).id('waystones:warp_stone')
})