lp user @s permission set kits.kit.weekly_1 true
scoreboard players set @s gangs_perm_w1 1
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Week 1 Kit is available! Use ","color":"green"},{"text":"/kits","color":"yellow","bold":true},{"text":" to claim it.","color":"green"}]
