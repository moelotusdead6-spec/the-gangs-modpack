loot give @s loot gangs:kits/monthly
cosmetickeyinternal @s
cosmetickeyinternal @s
cosmetickeyinternal @s
rc monthly_bow_staff @s 1
rc monthly_trinket_gem_totem @s 1
lp user @s permission set kits.kit.4_monthly false
scoreboard players set @s gangs_claimed_m 1
scoreboard players set @s gangs_perm_m 0
playsound minecraft:entity.player.levelup player @s ~ ~ ~ 1 1 1
tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Monthly Kit claimed!","color":"green"}]
