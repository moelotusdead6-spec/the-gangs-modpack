loot give @s loot gangs:kits/monthly
lp user @s permission set kits.kit.4_monthly false
scoreboard players set @s gangs_claimed_m 1
scoreboard players set @s gangs_perm_m 0
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Monthly Kit claimed!","color":"green"}]
