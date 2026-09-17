loot give @s loot gangs:kits/weekly_1
cosmeticgiveinternal @s
lp user @s permission set kits.kit.1_weekly_1 false
scoreboard players set @s gangs_claimed_w1 1
scoreboard players set @s gangs_perm_w1 0
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1 1
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Week 1 Kit claimed!","color":"green"}]
