execute in gangs:hub run setblock 0 67 0 minecraft:stone
execute in gangs:hub run setworldspawn 0 68 0
execute in gangs:hub as @e[type=minecraft:text_display,tag=gangs_hub_commands] run data modify entity @s text set value '{"color":"white","text":"Useful Commands:\n/hub /wild /rtp /shop /claim /tpa /tpahere /gs"}'