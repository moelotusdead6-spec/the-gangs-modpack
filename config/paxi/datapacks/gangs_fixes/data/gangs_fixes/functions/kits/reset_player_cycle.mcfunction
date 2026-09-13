scoreboard players operation @s gangs_cycle_start = #epoch gangs_time
scoreboard players set @s gangs_claimed_w1 0
scoreboard players set @s gangs_claimed_w2 0
scoreboard players set @s gangs_claimed_w3 0
scoreboard players set @s gangs_claimed_m 0

lp user @s permission set kits.kit.weekly_1 false
lp user @s permission set kits.kit.weekly_2 false
lp user @s permission set kits.kit.weekly_3 false
lp user @s permission set kits.kit.monthly false

scoreboard players set @s gangs_perm_w1 0
scoreboard players set @s gangs_perm_w2 0
scoreboard players set @s gangs_perm_w3 0
scoreboard players set @s gangs_perm_m 0

tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Your 30-day kit cycle has reset! Week 1 Kit is now available.","color":"green"}]
