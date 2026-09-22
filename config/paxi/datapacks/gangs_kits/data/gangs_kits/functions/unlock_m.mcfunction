kits resetusage @s 4_monthly
lp user @s permission set kits.kit.4_monthly true
scoreboard players set @s gangs_perm_m 1
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Monthly Kit is now UNLOCKED! Use ","color":"green"},{"text":"/kits","color":"yellow","bold":true},{"text":" to claim it.","color":"green"}]
