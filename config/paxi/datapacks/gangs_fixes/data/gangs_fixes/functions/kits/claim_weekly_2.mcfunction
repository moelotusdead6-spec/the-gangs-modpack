function gangs_fixes:kits/count_occupied_slots
execute if score #occupied gangs_temp matches 27.. run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 10 empty inventory slots to claim Week 2 Kit!","color":"red"}]
execute if score #occupied gangs_temp matches ..26 run function gangs_fixes:kits/do_claim_w2
