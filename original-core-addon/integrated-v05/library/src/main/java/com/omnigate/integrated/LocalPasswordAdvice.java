package com.omnigate.integrated;

import java.security.SecureRandom;
import java.util.Locale;

/** Local, offline password advice. Scores are heuristics, not entropy or breach tests. */
final class LocalPasswordAdvice {
    private static final SecureRandom RANDOM=new SecureRandom();
    private static final String LOWER="abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER="ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS="23456789";
    private static final String SYMBOLS="!@#$%&*+-_?";
    private static final String[] COMMON={"password","password123","qwerty","qwerty123",
        "123456","123456789","12345678","1234567890","111111","admin","admin123",
        "letmein","welcome","iloveyou","abc123","000000","passw0rd","changeme"};
    private LocalPasswordAdvice(){}
    static String generate(int length,boolean symbols){
        if(length<16||length>64)throw new IllegalArgumentException("length");
        return generateCustom(length,true,true,true,symbols,true);
    }
    static String generateCustom(int length,boolean lower,boolean upper,boolean digits,
            boolean symbols,boolean excludeAmbiguous){
        if(length<12||length>64||!(lower||upper||digits||symbols))
            throw new IllegalArgumentException("generator_options");
        String l=excludeAmbiguous?LOWER:"abcdefghijklmnopqrstuvwxyz";
        String u=excludeAmbiguous?UPPER:"ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String d=excludeAmbiguous?DIGITS:"0123456789";
        String alphabet=(lower?l:"")+(upper?u:"")+(digits?d:"")+(symbols?SYMBOLS:"");
        for(int attempt=0;attempt<4096;attempt++){
            char[] chars=new char[length];
            for(int i=0;i<length;i++)chars[i]=alphabet.charAt(RANDOM.nextInt(alphabet.length()));
            String candidate=new String(chars);
            java.util.Arrays.fill(chars,'\0');
            if((!lower||has(candidate,l))&&(!upper||has(candidate,u))&&
                (!digits||has(candidate,d))&&(!symbols||has(candidate,SYMBOLS)))return candidate;
        }
        throw new IllegalStateException("generator_unavailable");
    }
    private static boolean has(String value,String alphabet){
        for(int i=0;i<value.length();i++)if(alphabet.indexOf(value.charAt(i))>=0)return true;
        return false;
    }
    static int score(String password,String username,boolean reused){
        if(password==null||password.isEmpty())return 0;
        String p=password.toLowerCase(Locale.ROOT);
        int n=password.codePointCount(0,password.length());
        for(String weak:COMMON)if(p.equals(weak)||p.matches(java.util.regex.Pattern.quote(weak)+"[!@#$%&*0-9]{0,8}"))return 10;
        if(p.matches("(.)\\1{3,}")||p.matches("[0-9]+")&&n<16)return 10;
        for(String sequence:new String[]{"1234567890","abcdefghijklmnopqrstuvwxyz","qwertyuiop","asdfghjkl","9876543210"})
            if(n>=4&&sequence.contains(p))return 10;
        for(int period=1;period<=Math.min(6,n/2);period++){
            if(password.length()%period!=0)continue;
            boolean repeat=true;
            for(int i=period;i<password.length();i++)if(password.charAt(i)!=password.charAt(i%period)){repeat=false;break;}
            if(repeat)return 15;
        }
        int variety=0;
        if(password.chars().anyMatch(Character::isLowerCase))variety++;
        if(password.chars().anyMatch(Character::isUpperCase))variety++;
        if(password.chars().anyMatch(Character::isDigit))variety++;
        if(password.chars().anyMatch(c->!Character.isLetterOrDigit(c)))variety++;
        int result=Math.min(80,n*4)+Math.max(0,variety-1)*5;
        if(n<8)result=Math.min(result,25);
        else if(n<12)result=Math.min(result,55);
        else if(n<16)result=Math.min(result,75);
        if(containsUsername(password,username))result=Math.min(result,35);
        // Length alone does not establish the strength of all-numeric passwords.
        if(p.matches("[0-9]+"))result=Math.min(result,55);
        if(reused)result=Math.min(result,40);
        return Math.min(100,result);
    }
    static boolean containsUsername(String password,String username){
        String user=username==null?"":username.trim().toLowerCase(Locale.ROOT);
        int at=user.indexOf('@');if(at>=0)user=user.substring(0,at);
        return password!=null&&user.length()>=4&&password.toLowerCase(Locale.ROOT).contains(user);
    }
    // Bit flags allow categories to overlap without inflating affected-record count.
    static int risks(String password,String username,boolean reused){
        if(password==null)password="";
        return (score(password,username,false)<60?1:0)|(reused?2:0)|
            (password.codePointCount(0,password.length())<15?4:0)|
            (containsUsername(password,username)?8:0);
    }
    static String label(int score){
        return score<30?"弱":score<60?"一般":score<80?"较强":"强";
    }
}
