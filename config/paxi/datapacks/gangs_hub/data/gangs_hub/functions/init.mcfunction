execute in gangs:hub run setblock 0 67 0 minecraft:bedrock
execute in gangs:hub run setworldspawn 0 68 0 0
execute in gangs:hub run time set 18000
execute in gangs:hub run kill @e[type=minecraft:text_display,tag=gangs_hub_commands]
execute in gangs:hub run summon minecraft:text_display -6 62 28 {Tags:["gangs_hub_commands"],text:'{"text":"Travel & Homes\\n/hub | /wild | /rtp\\n/sethome <name> | /home <name>\\n/home list | /phome <name> | /phome list\\n/tpa <player> | /tpahere <player>\\n/tpaccept | /tpdecline","color":"aqua"}',text_opacity:191b,billboard:"center",background:0,shadow:0b}
execute in gangs:hub run kill @e[type=minecraft:text_display,tag=gangs_hub_shop_pricing_advice]
execute in gangs:hub run summon minecraft:text_display 6 62 28 {Tags:["gangs_hub_shop_pricing_advice"],text:'{"text":"Claims\\nGold shovel: right-click 2 corners\\n/claim info | /claim list | /claim visualize\\n/claim trust <player>\\n/claim trust interact <player>\\n/claim untrust <player>\\n/claim untrust interact <player>\\n/claim unclaim","color":"gold"}',text_opacity:191b,billboard:"center",background:0,shadow:0b}
execute in gangs:hub run kill @e[type=minecraft:text_display,tag=gangs_hub_kits_utilities]
execute in gangs:hub run summon minecraft:text_display 0 62 32 {Tags:["gangs_hub_kits_utilities"],text:'{"text":"Kits & Utilities\\n/kits\\n/gs | /gs mine | /gs history\\n/gs add <amount> <price>\\n/ec | /feed\\n/hat | /hats | /nickname <name>","color":"light_purple"}',text_opacity:191b,billboard:"center",background:0,shadow:0b}
execute in gangs:hub run kill @e[type=minecraft:text_display,tag=gangs_hub_welcome]
execute in gangs:hub run summon minecraft:text_display 0 70 6 {Tags:["gangs_hub_welcome"],text:'{"text":"Welcome Gang!","color":"gold"}',text_opacity:191b,billboard:"center",background:0,shadow:0b,transformation:{scale:[2.0f,2.0f,2.0f]}}
execute in gangs:hub run kill @e[type=minecraft:text_display,tag=gangs_hub_special_thanks]
execute in gangs:hub run summon minecraft:text_display 0 69 53 {Tags:["gangs_hub_special_thanks"],text:'{"text":"Special thanks!\\nThe Original Gang!","color":"gold"}',text_opacity:191b,billboard:"center",background:0,shadow:0b,transformation:{translation:[0.0f,0.0f,0.0f],left_rotation:[0.0f,0.0f,0.0f,1.0f],scale:[5.0f,5.0f,5.0f],right_rotation:[0.0f,0.0f,0.0f,1.0f]}}