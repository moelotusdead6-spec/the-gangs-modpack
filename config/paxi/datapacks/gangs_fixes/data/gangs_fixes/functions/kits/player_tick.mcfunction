# Initialize cycle start for new players
execute if score @s gangs_cycle_start matches 0 run function gangs_fixes:kits/init_player

# Calculate time elapsed in seconds: gangs_temp = #epoch - gangs_cycle_start
scoreboard players operation #temp gangs_temp = #epoch gangs_time
scoreboard players operation #temp gangs_temp -= @s gangs_cycle_start

# If 30 days (2,592,000 seconds) have elapsed, reset this player's 30-day cycle
execute if score #temp gangs_temp matches 2592000.. run function gangs_fixes:kits/reset_player_cycle

# Recalculate time elapsed after possible reset
scoreboard players operation #temp gangs_temp = #epoch gangs_time
scoreboard players operation #temp gangs_temp -= @s gangs_cycle_start

# Week 1: Unlocks immediately (elapsed >= 0)
execute if score #temp gangs_temp matches 0.. if score @s gangs_claimed_w1 matches 0 if score @s gangs_perm_w1 matches 0 run function gangs_fixes:kits/unlock_w1

# Week 2: Unlocks after 7 days (604,800 seconds)
execute if score #temp gangs_temp matches 604800.. if score @s gangs_claimed_w2 matches 0 if score @s gangs_perm_w2 matches 0 run function gangs_fixes:kits/unlock_w2

# Week 3: Unlocks after 14 days (1,209,600 seconds)
execute if score #temp gangs_temp matches 1209600.. if score @s gangs_claimed_w3 matches 0 if score @s gangs_perm_w3 matches 0 run function gangs_fixes:kits/unlock_w3

# Monthly: Unlocks after 21 days (1,814,400 seconds)
execute if score #temp gangs_temp matches 1814400.. if score @s gangs_claimed_m matches 0 if score @s gangs_perm_m matches 0 run function gangs_fixes:kits/unlock_m
