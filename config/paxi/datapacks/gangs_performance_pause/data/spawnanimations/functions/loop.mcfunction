# Slow recovery also handles mobs in chunks loaded after startup.
schedule function spawnanimations:loop 5s replace
execute as @e[tag=ts.sa.to_hide] run function spawnanimations:internal/entity/ehs/hide_exec
execute as @e[tag=ts.sa.to_verify] at @s run function spawnanimations:internal/animation/dig_up/verify_in_air
execute as @e[tag=ts.sa.verify,scores={ts.sa.e.y=-2147483648..2147483647}] store result entity @s Pos[1] double 0.01 run scoreboard players get @s ts.sa.e.y
execute as @e[tag=ts.sa.verify] at @s run function spawnanimations:internal/animation/dig_up/verify_in_air
