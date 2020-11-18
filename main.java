import java.util.*;
import java.io.*;
import java.math.*;

class Player {

    public static void main(String args[]) {

        Scanner in = new Scanner(System.in);

        while (true) {
            long startTime = System.nanoTime();

            ArrayList<Integer> inventory = new ArrayList<>();
            ArrayList<Potion> potions = new ArrayList<>();
            ArrayList<Spell> spells = new ArrayList<>();
            ArrayList<LearnableSpell> learnableSpells = new ArrayList<>();

            int actionCount = in.nextInt(); // the number of spells and recipes in play
            for (int i = 0; i < actionCount; i++) {
                int actionId = in.nextInt(); // the unique ID of this spell or recipe
                String actionType = in.next(); // BREW, CAST, OPPONENT_CAST, LEARN
                int delta0 = in.nextInt(); // tier-0 ingredient change
                int delta1 = in.nextInt(); // tier-1 ingredient change
                int delta2 = in.nextInt(); // tier-2 ingredient change
                int delta3 = in.nextInt(); // tier-3 ingredient change
                int price = in.nextInt(); // the price in rupees if this is a potion
                int tomeIndex = in.nextInt(); // in the first two leagues: always 0; later: the index in the tome if this is a tome spell, equal to the read-ahead tax
                int taxCount = in.nextInt(); // in the first two leagues: always 0; later: the amount of taxed tier-0 ingredients you gain from learning this spell
                boolean castable = in.nextInt() != 0; // in the first league: always 0; later: 1 if this is a castable player spell
                boolean repeatable = in.nextInt() != 0; // for the first two leagues: always 0; later: 1 if this is a repeatable player spell

                if ("BREW".equals(actionType)) {
                    int[] cost = {delta0, delta1, delta2, delta3};
                    potions.add(new Potion(actionId, cost, price));
                } else if ("CAST".equals(actionType)) {
                    int[] cost = {delta0, delta1, delta2, delta3};
                    spells.add(new Spell(actionId, cost, castable, repeatable));
                } else if ("LEARN".equals(actionType)) {
                    int[] cost = {delta0, delta1, delta2, delta3};
                    learnableSpells.add(new LearnableSpell(actionId, cost, tomeIndex, taxCount, repeatable));
                }
            }

            for (int i = 0; i < 2; i++) {
                int inv0 = in.nextInt(); // tier-0 ingredients in inventory
                int inv1 = in.nextInt();
                int inv2 = in.nextInt();
                int inv3 = in.nextInt();
                int score = in.nextInt(); // amount of rupees
                if (i == 0) {
                    inventory.add(inv0);
                    inventory.add(inv1);
                    inventory.add(inv2);
                    inventory.add(inv3);
                }
            }

            // BREW <id> | CAST <id> ?<times>? | LEARN <id> | REST
            String answer = myAction(potions, inventory, spells, learnableSpells);
            long endTime = System.nanoTime();
            long duration = (endTime - startTime) / 1000000; 
            System.out.println(answer + " Time: " + duration + "ms");
        }
    }

    public static String myAction(ArrayList<Potion> potions, ArrayList<Integer> invent, ArrayList<Spell> spells, ArrayList<LearnableSpell> learnableSpells) {

        int iterations = 0;
        int[] inventory = convert(invent);
        Potion bestPotion = identifyBestPotion(potions, inventory);
        LearnableSpell bestLearnableSpell = identifyBestLearnableSpell(learnableSpells);
        ArrayList<Spell> spells2 = orderSpellsByUsefulness(spells, inventory, bestPotion);

        if (canMakePotion(bestPotion, inventory)) {
            return "BREW " + bestPotion.id;
        } else if (canLearnSpell(bestLearnableSpell, spells, inventory)) {
            spells.add(new Spell(0, bestLearnableSpell.delta, true, bestLearnableSpell.repeatable));
            return "LEARN " + bestLearnableSpell.id;
        } else {
            for (Spell spell : spells2) {
                if (spell.remainder == 0 
                && haveSpaceInInventory(spell.cost, inventory) 
                && haveIngredients(spell.cost, inventory)) {
                    return (spell.castable) ? "CAST " + spell.id : "REST";
                }
                if (canCastSpell(spell, inventory)) {
                    for (int i = 3; i >= 0; i--) { // for all the ingredients
                        if (spell.cost[i] > 0) { //if im making this ingredient
                            if (inventory[i] < Math.abs(bestPotion.cost[i])) { // if i need more of this ingredient for the potion
                                return "CAST " + spell.id;
                            } else { //if i need more of this ingredient to make a higher tier ingredient
                                if (i != 3) { // yellows dont count
                                    for (int x = 3; x > i; x--) { // for all the colours above this one (inclusive)
                                        int calc = 0;
                                        for (int y = 3; y > x; y--) {
                                            calc += (inventory[y] - Math.abs(bestPotion.cost[y]));
                                        }
                                        if (Math.abs(bestPotion.cost[x]) > inventory[x] && calc < Math.abs(bestPotion.cost[x])) {
                                            return "CAST " + spell.id;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return "REST";
        }
    }

    public static float calculate(int[] spellCost, int[] potionCost, int[] inventory, int equation) {
        
        float answer = 0;
        for (int i = 0; i < inventory.length; i++) {
            int calc = 0;
            if (equation == 1) { // How close the inventory gets to the potion after using the spell
                calc = (Math.abs(potionCost[i]) - (inventory[i] + spellCost[i])) * (i + 1);
            } else if (equation == 2) { // Total cost of potion ingredients
                calc = (Math.abs(potionCost[i]) * (i + 1));
            } else if (equation == 3) { // How "worth" it is to work towards the potion given the inventory
                calc = (Math.abs(potionCost[i]) - inventory[i]) * (i + 1);
            }
            if (calc < 0) {
                calc = 0;
            }
            answer += calc;
        }
        return answer;
    }

    public static boolean canCastSpell(Spell spell, int[] inventory) {
        return !(!spell.castable || !haveSpaceInInventory(spell.cost, inventory) || !haveIngredients(spell.cost, inventory)); 
    }

    public static boolean canLearnSpell(LearnableSpell learnableSpell, ArrayList<Spell> spells, int[] inventory) {
        return learnableSpellValue(learnableSpell) >= 3 && spells.size() < 10 && learnableSpell.tomeIndex <= inventory[0];
    }

    public static boolean canMakePotion(Potion potion, int[] inventory) {
        for (int i = 0; i < inventory.length; i++) {
            if (potion.cost[i] + inventory[i] < 0) {
                return false;
            }
        }
        return true;
    }

    public static int[] convert(ArrayList<Integer> list) {
        int[] out = new int[list.size()];
        Arrays.setAll(out, list::get);
        return out;
    }

    public static boolean haveIngredients(int[] delta, int[] inventory) {
        for (int i = 0; i < delta.length; i++) {
            if (delta[i] < 0 && Math.abs(delta[i]) > inventory[i]) {
                return false;
            }
        }
        return true;
    }

    public static boolean haveSpaceInInventory(int[] cost, int[] inventory) {
        return Arrays.stream(cost).sum() <= (10 - Arrays.stream(inventory).sum());
    }

    public static LearnableSpell identifyBestLearnableSpell(ArrayList<LearnableSpell> learnableSpells) {
        LearnableSpell bestLearnableSpell = learnableSpells.get(0);
        for (int i = 1; i < learnableSpells.size(); i++) {
            if (learnableSpellValue(learnableSpells.get(i)) > learnableSpellValue(bestLearnableSpell)) {
                bestLearnableSpell = learnableSpells.get(i);
            }
        }
        return bestLearnableSpell;
    }

    public static Potion identifyBestPotion(ArrayList<Potion> potions, int[] inventory) {
        Potion bestPotion = potions.get(0);
        for (int i = 1; i < potions.size(); i++) {
            if (potionValue(potions.get(i), inventory) < potionValue(bestPotion, inventory)) {
                bestPotion = potions.get(i);
            }
        }
        return bestPotion;
    }

    public static int learnableSpellValue(LearnableSpell learnableSpell) {
        int value = 0;
        boolean free = true;
        for (int i = 0; i < learnableSpell.delta.length; i++) {
            if (learnableSpell.delta[i] < -1) {
                free = false;
            }
            value += (learnableSpell.delta[i] * (i + 1));
        }
        if (free) {
            value += 1;
        }
        return value;
    }

    public static ArrayList<Spell> orderSpellsByUsefulness(ArrayList<Spell> spells, int[] inventory, Potion potion) {
        ArrayList<Spell> spells2 = new ArrayList<>();
        for (int i = 0; i < spells.size(); i++) {
            spells.get(i).remainder = (int) calculate(spells.get(i).cost, potion.cost, inventory, 1);
            spells2.add(spells.get(i));
        }
        Collections.sort(spells2, Comparator.comparingInt(Spell::getRemainder));
        return spells2;
    }

    public static float potionValue(Potion potion, int[] inventory) {

        float totalCost = calculate(null, potion.cost, inventory, 2);
        float efficiency = ((float) potion.reward) / totalCost;
        if (efficiency < 1.2) {
            efficiency = (float) 0.1;
        }
        float sum = calculate(null, potion.cost, inventory, 3) / efficiency;
        potion.distanceToMaking = sum;
        return sum;
    }
}

class Potion {

    int id;
    int[] cost;
    int reward;
    float distanceToMaking;

    public Potion(int id, int[] cost, int reward) {
        this.id = id;
        this.cost = cost;
        this.reward = reward;
        distanceToMaking = 100;
    }
}

class Spell {

    int id;
    int[] cost;
    boolean castable;
    boolean repeatable;
    int remainder;

    public Spell(int id, int[] cost, boolean castable, boolean repeatable) {
        this.id = id;
        this.cost = cost;
        this.castable = castable;
        this.repeatable = repeatable;
        remainder = 100;
    }

    public int getRemainder() {
        return remainder;
    }
}

class LearnableSpell {

    int id;
    int[] delta;
    int tomeIndex;
    int taxCount;
    boolean repeatable;

    public LearnableSpell(int id, int[] delta, int tomeIndex, int taxCount, boolean repeatable) {
        this.id = id;
        this.delta = delta;
        this.tomeIndex = tomeIndex;
        this.taxCount = taxCount;
        this.repeatable = repeatable;
    }
}
