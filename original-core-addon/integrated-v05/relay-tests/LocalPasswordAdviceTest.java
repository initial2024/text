package com.omnigate.integrated;
import java.util.HashSet;

public final class LocalPasswordAdviceTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError("password_advice_test_failed");}
    public static void main(String[] args){
        HashSet<String> generated=new HashSet<>();
        for(int n:new int[]{16,20,24,32,64})for(boolean symbols:new boolean[]{false,true}){
            for(int i=0;i<64;i++){
                String value=LocalPasswordAdvice.generate(n,symbols);
                check(value.length()==n);
                check(value.matches(".*[A-Z].*")&&value.matches(".*[a-z].*")&&value.matches(".*[0-9].*"));
                check(symbols==value.chars().anyMatch(c->!Character.isLetterOrDigit(c)));
                check(generated.add(value));
                check(LocalPasswordAdvice.score(value,"",false)>=64);
            }
        }
        check(LocalPasswordAdvice.score("", "",false)==0);
        check(LocalPasswordAdvice.score("password123!","",false)<=10);
        check(LocalPasswordAdvice.score("123456789012345","",false)<=10);
        check(LocalPasswordAdvice.score("abcdabcdabcdabcd","",false)<=15);
        check(LocalPasswordAdvice.score("Sammy2026!Longer","sammy@example.com",false)<=35);
        check(LocalPasswordAdvice.score("VeryLongRandomFixture982@","",true)<=40);
        check(LocalPasswordAdvice.score("aB3!xyz","",false)<=25);
        check(LocalPasswordAdvice.risks("password","",true)==7);
        check(LocalPasswordAdvice.risks("Sammy2026!Longer","sammy@example.com",false)==9);
        check(LocalPasswordAdvice.score("928316529184275618453","",false)<=55);
        check(LocalPasswordAdvice.risks("H7!kW9&uT2#rL4@q","",false)==0);
        check(LocalPasswordAdvice.risks("H7!kW9&uT2#rL4@q","",true)==2);
        boolean invalid=false;
        try{LocalPasswordAdvice.generate(15,true);}catch(IllegalArgumentException e){invalid=true;}
        check(invalid);
        System.out.println("OGVAULT12_GENERATOR_640_SYNTHETIC_CASES=PASS");
        System.out.println("OGVAULT12_WEAK_REUSED_AND_LENGTH_CHECKS=PASS");
        System.out.println("OGVAULT12_SCORE_IS_HEURISTIC=YES");
        System.out.println("OGVAULT12_HEALTH_RISK_FLAGS=PASS");
    }
}
