EntityEvents.spawned(event => {
    if (event.entity.type === 'galosphere:sparkle') {
        event.entity.kill()
    }
})
