function gangs_fixes:kits/count_occupied_slots
execute if score #occupied gangs_temp matches 23.. run tellraw @s ["",{"text":"[Kits] ","color":"gold","bold":true},{"text":"Claim failed: You need at least 14 empty inventory slots to claim Monthly Kit!","color":"red"}]
execute if score #occupied gangs_temp matches ..22 run function gangs_fixes:kits/do_claim_m
