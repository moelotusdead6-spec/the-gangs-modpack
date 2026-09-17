loot give @s loot gangs:kits/weekly_2
cosmeticgiveinternal @s
lp user @s permission set kits.kit.2_weekly_2 false
scoreboard players set @s gangs_claimed_w2 1
scoreboard players set @s gangs_perm_w2 0
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1 1
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Week 2 Kit claimed!","color":"green"}]
