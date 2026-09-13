loot give @s loot gangs:kits/weekly_3
lp user @s permission set kits.kit.weekly_3 false
scoreboard players set @s gangs_claimed_w3 1
scoreboard players set @s gangs_perm_w3 0
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Week 3 Kit claimed!","color":"green"}]
