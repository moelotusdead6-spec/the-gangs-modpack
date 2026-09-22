kits resetusage @s 2_weekly_2
lp user @s permission set kits.kit.2_weekly_2 true
scoreboard players set @s gangs_perm_w2 2
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Week 2 Kit is now UNLOCKED! Use ","color":"green"},{"text":"/kits","color":"yellow","bold":true},{"text":" to claim it.","color":"green"}]
