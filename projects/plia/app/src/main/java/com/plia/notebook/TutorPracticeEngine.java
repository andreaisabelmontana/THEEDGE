package com.plia.notebook;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Original local exercises. No model, network, file access or symbolic/handwriting grader. */
public final class TutorPracticeEngine {
    private TutorPracticeEngine() {}
    public static final String GENERATOR_VERSION="tutor-original-v1", VALIDATION_VERSION="analytic-v1";
    public static final String NUMERIC_CHECK_NOTE="Enter a fraction or a decimal to at least 6 places. Checks only the final numeric value, not handwriting, reasoning or method.";
    public enum AnswerKind { NUMERIC, REVIEW_REQUIRED }
    public enum CheckResult { CORRECT, INCORRECT, INVALID_INPUT, REVIEW_REQUIRED }
    private static final Gson JSON=new GsonBuilder().serializeNulls().disableHtmlEscaping().create();

    public static final class Step {
        public final String math, words;
        public Step(String math,String words){this.math=required(math);this.words=required(words);}
    }
    public static final class PracticeItem {
        public final String topicId,templateId,itemKey,generatorVersion,validationVersion,domain,promptLatex,promptWords,answerLatex;
        public final long seed;
        public final Map<String,Integer> parameters;
        public final List<Step> steps;
        public final List<String> hints;
        public final AnswerKind answerKind;
        public final Double numericAnswer,tolerance;
        private PracticeItem(String topic,String family,long seed,Map<String,Integer> params,String domain,
                String prompt,String words,String answer,Double numeric,List<Step> steps,List<String> hints){
            this.topicId=required(topic);this.templateId=required(family);this.seed=seed;
            this.generatorVersion=GENERATOR_VERSION;this.validationVersion=VALIDATION_VERSION;
            this.parameters=Collections.unmodifiableMap(new LinkedHashMap<>(params));
            StringBuilder key=new StringBuilder(GENERATOR_VERSION).append(':').append(family);
            for(Map.Entry<String,Integer> entry:new java.util.TreeMap<>(params).entrySet())key.append(';').append(entry.getKey()).append('=').append(entry.getValue());
            this.itemKey=key.toString();if(itemKey.length()>512)throw new IllegalArgumentException("Exercise identity too long");
            this.domain=required(domain);this.promptLatex=required(prompt);this.promptWords=required(words)+(numeric==null?"":" "+NUMERIC_CHECK_NOTE);this.answerLatex=required(answer);
            this.steps=Collections.unmodifiableList(new ArrayList<>(steps));this.hints=Collections.unmodifiableList(new ArrayList<>(hints));
            if(steps.isEmpty()||hints.size()<2)throw new IllegalArgumentException("Exercise needs worked steps and progressive hints");
            if(numeric!=null && !Double.isFinite(numeric))throw new IllegalArgumentException("Nonfinite answer");
            numericAnswer=numeric;answerKind=numeric==null?AnswerKind.REVIEW_REQUIRED:AnswerKind.NUMERIC;
            // Bounded numeric values and explicit six-decimal approximation; never grade an exact symbolic claim.
            tolerance=numeric==null?null:5.1e-7;
        }
        public String toPairedMarkdown(){
            StringBuilder out=new StringBuilder(promptWords).append("\n\n\\[").append(promptLatex).append("\\]\n\n");
            out.append("| Steps in math | Steps in words |\n| --- | --- |\n");
            for(Step s:steps)out.append("| \\(").append(s.math.replace("\n"," ")).append("\\) | ").append(cell(s.words)).append(" |\n");
            out.append("\nFinal answer\n\\[").append(answerLatex).append("\\]\n");
            return out.toString();
        }
        public String toSnapshotJson(){return JSON.toJson(this);}
    }
    private static final class Build {
        final String topic,family;final long seed;final Map<String,Integer> params=new LinkedHashMap<>();
        final List<Step> steps=new ArrayList<>();final List<String> hints=new ArrayList<>();
        Build(String topic,String family,long seed){this.topic=topic;this.family=family;this.seed=seed;}
        int p(String key,int value){params.put(key,value);return value;}
        Build s(String math,String words){steps.add(new Step(math,words));return this;}
        Build h(String...values){hints.addAll(Arrays.asList(values));return this;}
        PracticeItem item(String domain,String prompt,String words,String answer,Double numeric){
            return new PracticeItem(topic,family,seed,params,domain,prompt,words,answer,numeric,steps,hints);
        }
    }
    public static PracticeItem canonical(String familyId){return generate(familyId,0);}
    public static PracticeItem generate(String familyId,long seed){
        if(familyId==null)throw new IllegalArgumentException("Unknown practice family");
        Random random=new Random(seed ^ ((long)familyId.hashCode()<<32) ^ 0x4f726967696e616cL);
        switch(familyId){
            case "rational-hole":return rational(familyId,seed,random);
            case "epsilon-delta-square":return epsilon(familyId,seed,random);
            case "implicit-polynomial-trig":return implicit(familyId,seed,random);
            case "ftc-two-variable-bounds":return ftc(familyId,seed,random);
            case "mixed-substitution-parts":return mixed(familyId,seed,random);
            case "geometric-area-sign-split":return area(familyId,seed,random);
            case "reverse-double-integral":return reverse(familyId,seed,random);
            case "two-series-tests":return series(familyId,seed,random);
            case "cubic-exponential-maclaurin":return maclaurin(familyId,seed,random);
            case "normalized-directional-derivative":return directional(familyId,seed,random);
            case "tangent-plane-quadratic":return tangent(familyId,seed,random);
            case "unconstrained-quadratic-and-state-only-constraint":return optimization(familyId,seed,random);
            case "conservative-field-integral":return field(familyId,seed,random);
            case "triangular-lamina-mass":return mass(familyId,seed,random);
            default:throw new IllegalArgumentException("Unknown practice family: "+familyId);
        }
    }
    /** Restores only a supported, unmodified original exercise; never trusts a stored answer. */
    public static PracticeItem fromSnapshotJson(String supplied){
        if(supplied==null||supplied.length()>32000)throw new IllegalArgumentException("Invalid practice snapshot size");
        try {
            JsonElement element=JsonParser.parseString(supplied);
            if(!element.isJsonObject())throw new IllegalArgumentException("Expected practice object");
            JsonObject object=element.getAsJsonObject();
            String seedText=object.get("seed").getAsString();
            if(!seedText.matches("-?(0|[1-9][0-9]*)"))throw new IllegalArgumentException("Invalid seed");
            PracticeItem expected=generate(object.get("templateId").getAsString(),Long.parseLong(seedText));
            if(!JSON.toJsonTree(expected).equals(element))throw new IllegalArgumentException("Unknown version or modified practice snapshot");
            return expected;
        } catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid or unsupported practice snapshot",invalid);}
    }
    public static CheckResult checkNumeric(PracticeItem item,String supplied){
        if(item==null)throw new IllegalArgumentException("Missing exercise");
        if(item.answerKind!=AnswerKind.NUMERIC)return CheckResult.REVIEW_REQUIRED;
        if(supplied==null||supplied.length()>96)return CheckResult.INVALID_INPUT;
        String s=supplied.trim();String decimal="[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]{1,3})?";
        String[] fraction=s.split("/",-1);
        if(fraction.length>2||fraction.length==0)return CheckResult.INVALID_INPUT;
        try {
            for(String part:fraction)if(!part.trim().matches(decimal))return CheckResult.INVALID_INPUT;
            BigDecimal value=new BigDecimal(fraction[0].trim());
            if(fraction.length==2){BigDecimal denominator=new BigDecimal(fraction[1].trim());if(denominator.signum()==0)return CheckResult.INVALID_INPUT;value=value.divide(denominator,MathContext.DECIMAL128);}
            double answer=value.doubleValue();if(!Double.isFinite(answer)||answer==0&&value.signum()!=0)return CheckResult.INVALID_INPUT;
            double allowed=item.tolerance;
            return Math.abs(answer-item.numericAnswer)<=allowed?CheckResult.CORRECT:CheckResult.INCORRECT;
        } catch(NumberFormatException|ArithmeticException invalid){return CheckResult.INVALID_INPUT;}
    }
    private static int choose(long seed,Random r,int canonical,int low,int high){return seed==0?canonical:low+r.nextInt(high-low+1);}
    private static String required(String s){if(s==null||s.isEmpty())throw new IllegalArgumentException("Missing content");return s;}
    private static String cell(String s){return s.replace("|","\\|").replace("\n"," ");}
    private static String x(int coefficient,String variable){return coefficient==1?variable:coefficient==-1?"-"+variable:coefficient+variable;}
    private static String add(int coefficient,String variable){return coefficient<0?"-"+x(-coefficient,variable):"+"+x(coefficient,variable);}
    private static String frac(long n,long d){if(d==0)throw new IllegalArgumentException("Zero denominator");if(d<0){n=-n;d=-d;}long a=Math.abs(n),b=d;while(b!=0){long t=a%b;a=b;b=t;}long g=a==0?1:a;n/=g;d/=g;return d==1?Long.toString(n):(n<0?"-":"")+"\\frac{"+Math.abs(n)+"}{"+d+"}";}
    private static String exponent(int n){return n==1?"e":"e^{"+n+"}";}
    private static void need(boolean valid){if(!valid)throw new IllegalArgumentException("Degenerate template parameters");}

    private static PracticeItem rational(String id,long seed,Random r){
        Build b=new Build("limits-continuity-graphs",id,seed);int a=b.p("a",choose(seed,r,3,2,6));need(a>0);
        String p="f(x)=\\frac{x^2-"+(a*a)+"}{x^2-"+a+"x}";
        b.s("x(x-"+a+")\\ne0\\quad\\Rightarrow\\quad x\\ne0,"+a,"Find exclusions from the original denominator before cancelling.")
         .s("f(x)=\\frac{(x-"+a+")(x+"+a+")}{x(x-"+a+")}=1+\\frac{"+a+"}{x},\\quad x\\ne0,"+a,"Cancel only on the original domain; the missing point stays missing.")
         .s("\\lim_{x\\to "+a+"}f(x)=2","The excluded point at x="+a+" is a hole with height 2.")
         .s("\\lim_{x\\to0^+}f(x)=+\\infty,\\quad\\lim_{x\\to0^-}f(x)=-\\infty","The remaining denominator zero gives the vertical asymptote.")
         .s("\\lim_{x\\to\\pm\\infty}f(x)=1","The reciprocal term tends to zero, giving horizontal asymptote y=1.")
         .h("Factor numerator and denominator, but record exclusions first.","After cancellation use 1+a/x to distinguish the hole from the vertical asymptote.","Evaluate the simplified expression at the removed point, and take the limit at infinity.");
        return b.item("Real x; x != 0 and x != "+a,p,"Find the original domain, hole, vertical asymptote and horizontal asymptote.","x\\in\\mathbb R\\setminus\\{0,"+a+"\\};\\quad\\text{hole }("+a+",2);\\quad x=0;\\quad y=1",null);
    }
    private static PracticeItem epsilon(String id,long seed,Random r){
        Build b=new Build("limit-methods",id,seed);int a=b.p("a",choose(seed,r,2,1,6)),bound=2*a+1;need(a>0);
        b.s("|x^2-"+(a*a)+"|=|x-"+a+"|\\,|x+"+a+"|","Factor the difference; one factor is the distance we can control.")
         .s("|x-"+a+"|<1\\quad\\Rightarrow\\quad |x+"+a+"|<"+bound,"Restrict x to a one-unit neighborhood to bound the other factor.")
         .s("\\delta=\\min\\left(1,\\frac{\\varepsilon}{"+bound+"}\\right)","Make the distance small enough both for the neighborhood and the requested error.")
         .s("0<|x-"+a+"|<\\delta\\quad\\Rightarrow\\quad |x^2-"+(a*a)+"|<"+bound+"\\delta\\le\\varepsilon","This proves the epsilon-delta condition for every positive epsilon.")
         .h("Factor x^2-a^2.","Assume |x-a|<1; then |x+a|<2a+1.","Choose the smaller of 1 and epsilon/(2a+1).");
        return b.item("Real x; epsilon > 0; a="+a,"\\lim_{x\\to "+a+"}x^2="+(a*a),"Give an epsilon-delta proof. Use a direct bound, not L'Hopital's rule.","\\delta=\\min\\left(1,\\frac{\\varepsilon}{"+bound+"}\\right)",null);
    }
    private static PracticeItem implicit(String id,long seed,Random r){
        Build b=new Build("differentiation",id,seed);int m=b.p("m",seed==0?3:(r.nextBoolean()?3:5)),c=b.p("c",choose(seed,r,2,1,4));need(m>=3&&c>0);
        String derivative="-\\frac{2xy^{"+m+"}+"+c+"}{"+m+"x^2y^{"+(m-1)+"}+\\sin y}";
        b.s("2xy^{"+m+"}+"+m+"x^2y^{"+(m-1)+"}y'+"+c+"=-\\sin(y)y'","Differentiate the product; every derivative of y includes y'.")
         .s("("+m+"x^2y^{"+(m-1)+"}+\\sin y)y'=-(2xy^{"+m+"}+"+c+")","Move all y' terms to one side and factor them.")
         .s("y'="+derivative,"Divide only where the coefficient of y' is nonzero; leave the quotient in this form.")
         .h("Use the product rule on x^2 y^m and the chain rule on cos y.","Collect every term containing y' before dividing.","The denominator is m x^2 y^(m-1)+sin y; state its nonzero condition.");
        return b.item("Real points on the implicit curve where "+m+"*x^2*y^"+(m-1)+"+sin(y) != 0","x^2y^{"+m+"}+"+x(c,"x")+"=\\cos y","Find dy/dx and state where this implicit derivative formula is valid. Keep the factored quotient.","y'="+derivative+",\\quad "+m+"x^2y^{"+(m-1)+"}+\\sin y\\ne0",null);
    }
    private static PracticeItem ftc(String id,long seed,Random r){
        Build b=new Build("integration-ftc",id,seed);int a=b.p("a",choose(seed,r,1,1,3)),c=b.p("b",choose(seed,r,1,1,3)),k=b.p("k",1);need(a>0&&c>0&&k>0);
        int low=a*k*k,up=low+c*k,L=2*a*k,U=L+c;double answer=U*Math.exp(up)-L*Math.exp(low);need(Double.isFinite(answer));
        String lower=x(a,"x^2"),upper=lower+"+"+x(c,"x");
        b.s("F'(x)=e^{"+upper+"}("+x(2*a,"x")+"+"+c+")-e^{"+lower+"}("+x(2*a,"x")+")","Apply the FTC to the upper and lower bounds, multiplying each term by its own bound derivative.")
         .s("F'("+k+")="+U+exponent(up)+"-"+L+exponent(low),"Substitute the requested x value into both exponential arguments and both chain factors.")
         .h("For variable bounds use f(upper)*upper' - f(lower)*lower'.","The lower-bound contribution has a minus sign and a chain factor too.","Substitute x="+k+" only after differentiating.");
        return b.item("Real x; e^t is continuous; evaluate at x="+k,"F(x)=\\int_{"+lower+"}^{"+upper+"}e^t\\,dt","Find F'("+k+"). Show both chain-rule factors; a numeric check covers only the final value.",U+exponent(up)+"-"+L+exponent(low),answer);
    }
    private static PracticeItem mixed(String id,long seed,Random r){
        Build b=new Build("integration-ftc",id,seed);int a=b.p("a",choose(seed,r,4,2,6)),q=b.p("q",choose(seed,r,2,1,5)),c=b.p("c",choose(seed,r,3,1,4));need(a>0&&q>0&&c!=0);
        String anti=frac(a,3)+"(x^2+"+q+")^{3/2}+e^{"+x(c,"x")+"}\\left("+frac(1,c)+"x-"+frac(1,c*c)+"\\right)+C";
        b.s("u=x^2+"+q+",\\quad du=2x\\,dx","Use substitution on the radical term.")
         .s("\\int "+a+"x\\sqrt{x^2+"+q+"}\\,dx="+frac(a,2)+"\\int u^{1/2}\\,du="+frac(a,3)+"u^{3/2}","Account for the factor 2 in du, then integrate the power.")
         .s("u=x,\\quad dv=e^{"+x(c,"x")+"}dx,\\quad v="+frac(1,c)+"e^{"+x(c,"x")+"}","For the second term, use integration by parts with the polynomial as u.")
         .s("\\int xe^{"+x(c,"x")+"}dx="+frac(1,c)+"xe^{"+x(c,"x")+"}-"+frac(1,c*c)+"e^{"+x(c,"x")+"}","The remaining exponential integral contributes a second factor 1/"+c+".")
         .s(anti,"Combine both antiderivatives and include one arbitrary constant.")
         .h("Split the sum and choose a method for each term.","Use u=x^2+q for the radical term, and parts for x e^(c x).","Check by differentiation; the exponential constant term cancels the extra product-rule term.");
        return b.item("Real x; x^2+"+q+" > 0; integration constant C","\\int\\left["+a+"x\\sqrt{x^2+"+q+"}+xe^{"+x(c,"x")+"}\\right]dx","Integrate using substitution for the first term and integration by parts for the second.",anti,null);
    }
    private static PracticeItem area(String id,long seed,Random r){
        Build b=new Build("integration-ftc",id,seed);int a=b.p("a",choose(seed,r,2,1,4)),upper=b.p("b",seed==0?3:a+1+r.nextInt(3));need(upper>a&&a>0);
        long n=2L*upper*upper*upper-3L*a*upper*upper+2L*a*a*a;String answer=frac(n,6);
        b.s("f(x)=x(x-"+a+")","The zeros are 0 and "+a+"; the sign changes inside the integration interval.")
         .s("A=-\\int_0^{"+a+"}(x^2-"+a+"x)dx+\\int_{"+a+"}^{"+upper+"}(x^2-"+a+"x)dx","Reverse the sign below the axis; geometric area adds positive pieces.")
         .s("G(x)=\\frac{x^3}{3}-"+frac(a,2)+"x^2","Use this antiderivative separately on the two intervals.")
         .s("A=G("+upper+")-2G("+a+")+G(0)="+answer,"Add the two nonnegative areas instead of taking the absolute value of one signed integral.")
         .h("Factor the quadratic and find where its sign changes.","Split at x="+a+"; the first piece is below the axis.","Area = -integral from 0 to a + integral from a to b.");
        return b.item("Real x in [0,"+upper+"]; 0<"+a+"<"+upper,"y=x^2-"+a+"x,\\quad 0\\le x\\le "+upper,"Find the geometric area between this curve and the x-axis. Show the sign split.",answer,n/6.0);
    }
    private static PracticeItem reverse(String id,long seed,Random r){
        Build b=new Build("double-integrals-mass",id,seed);int a=b.p("a",choose(seed,r,2,1,4)),a2=a*a,a4=a2*a2;need(a>0);
        String answer="\\frac{1-e^{-"+a4+"}}{4}";
        b.s("0\\le x\\le "+a+",\\quad x^2\\le y\\le "+a2,"Describe the original region before changing the order.")
         .s("0\\le y\\le "+a2+",\\quad0\\le x\\le\\sqrt y","A horizontal slice starts at the y-axis and ends at the parabola.")
         .s("I=\\int_0^{"+a2+"}\\int_0^{\\sqrt y}xe^{-y^2}\\,dx\\,dy=\\frac12\\int_0^{"+a2+"}ye^{-y^2}\\,dy","Integrate x first; the upper x bound supplies y/2.")
         .s("I=\\left[-\\frac14e^{-y^2}\\right]_0^{"+a2+"}="+answer,"Now use the derivative of -y^2. The original inner integral need not be elementary.")
         .h("Sketch or describe x^2<=y<=a^2 with x>=0.","For fixed y, solve x^2<=y to get 0<=x<=sqrt(y).","After integrating x, the remaining y e^(-y^2) integral is a substitution.");
        return b.item("Compact region: 0<=x<="+a+", x^2<=y<="+a2,"I=\\int_0^{"+a+"}\\int_{x^2}^{"+a2+"}xe^{-y^2}\\,dy\\,dx","Reverse the integration order, then find the exact value. Review the bounds and the exponential term; this item is not auto-graded.",answer,null);
    }

    private static PracticeItem series(String id,long seed,Random r){
        Build b=new Build("improper-integrals-series",id,seed);int a=b.p("a",choose(seed,r,2,1,5)),d=b.p("d",choose(seed,r,7,2,9)),c=b.p("c",choose(seed,r,1,1,5));need(a>0&&d>0&&c>0);
        b.s("\\lim_{n\\to\\infty}\\frac{n^2+"+a+"}{"+d+"n^2+n}=\\frac1{"+d+"}\\ne0","Divide by n squared. A series cannot converge when its terms do not tend to zero.")
         .s("\\sum_{n=1}^{\\infty}\\frac{n^2+"+a+"}{"+d+"n^2+n}\\quad\\text{diverges}","The nonzero-term-limit test is already decisive; no more elaborate test is needed.")
         .s("\\left|\\frac{\\cos(n^3)}{n^2+"+c+"}\\right|\\le\\frac1{n^2+"+c+"}\\le\\frac1{n^2}","Take absolute values and bound cosine by 1.")
         .s("\\sum_{n=1}^{\\infty}\\frac1{n^2}<\\infty\\quad\\Rightarrow\\quad\\sum_{n=1}^{\\infty}\\frac{\\cos(n^3)}{n^2+"+c+"}\\text{ converges absolutely}","Comparison with the convergent p-series proves absolute convergence, hence convergence.")
         .h("Check the term limit before choosing a series test.","For the second series, bound the absolute value of cosine by 1.","Compare the second absolute series with sum 1/n^2; a zero term limit alone would not be enough.");
        return b.item("Integer n>=1; denominators positive","\\sum_{n=1}^{\\infty}\\frac{n^2+"+a+"}{"+d+"n^2+n},\\qquad\\sum_{n=1}^{\\infty}\\frac{\\cos(n^3)}{n^2+"+c+"}","Classify each series and justify the test. Do not claim a sum value from convergence alone.","\\text{First: divergent. Second: absolutely convergent.}",null);
    }
    private static PracticeItem maclaurin(String id,long seed,Random r){
        Build b=new Build("maclaurin",id,seed);int a=b.p("a",choose(seed,r,3,1,6));need(a!=0);
        String polynomial="1+"+x(a,"x")+"+"+frac(a*a,2)+"x^2+"+frac(a*a*a,6)+"x^3";
        b.s("P_3(x)=f(0)+f'(0)x+\\frac{f''(0)}{2!}x^2+\\frac{f'''(0)}{3!}x^3","Degree three means keeping powers through x cubed, including the constant.")
         .s("f^{(k)}(0)="+a+"^k","Each derivative of e^(a x) contributes another factor a; e^0=1.")
         .s("P_3(x)="+polynomial,"Insert the derivative values and divide by the factorials.")
         .h("Start with the general degree-three Maclaurin formula.","The kth derivative at zero is a^k.","Keep the factorial denominators 2 and 6; do not use raw derivatives as coefficients.");
        return b.item("Real x; polynomial centered at x=0; degree exactly3","f(x)=e^{"+x(a,"x")+"}","Find the degree-three Maclaurin polynomial. A polynomial expression requires self-review.","P_3(x)="+polynomial,null);
    }
    private static PracticeItem directional(String id,long seed,Random r){
        Build b=new Build("multivariable",id,seed);int p=b.p("p",choose(seed,r,1,1,4)),q=b.p("q",choose(seed,r,2,1,4));
        int[][] triples={{3,4,5},{5,12,13},{8,15,17}};int[] direction=triples[seed==0?0:r.nextInt(triples.length)];
        int u=b.p("u",direction[0]),v=b.p("v",direction[1]),norm=b.p("norm",direction[2]),d=1+p*p+q*q,n=2*(p*u+q*v);need(norm>0&&u*u+v*v==norm*norm);
        String answer=frac(n,d*norm);
        b.s("\\nabla f(x,y)=\\left(\\frac{2x}{1+x^2+y^2},\\frac{2y}{1+x^2+y^2}\\right)","Differentiate with respect to each variable, treating the other as constant.")
         .s("\\nabla f("+p+","+q+")=\\left("+frac(2*p,d)+","+frac(2*q,d)+"\\right)","Evaluate the gradient at the specified point.")
         .s("\\widehat{\\mathbf u}=\\frac{("+u+","+v+")}{"+norm+"}=\\left("+frac(u,norm)+","+frac(v,norm)+"\\right)","Normalize the direction; the directional derivative uses a unit vector.")
         .s("D_{\\widehat{\\mathbf u}}f=\\nabla f\\cdot\\widehat{\\mathbf u}="+answer,"Take the dot product of the evaluated gradient and unit direction.")
         .h("First find the two partial derivatives of the logarithm.","Divide the given direction by its Euclidean length.","Multiply corresponding components of the gradient and unit direction, then add.");
        return b.item("All real (x,y), since 1+x^2+y^2>0; nonzero direction ("+u+","+v+")","f(x,y)=\\ln(1+x^2+y^2),\\quad P=("+p+","+q+"),\\quad\\mathbf v=("+u+","+v+")","Find the scalar directional derivative at P in direction v. Show normalization.",answer,n/(double)(d*norm));
    }
    private static PracticeItem tangent(String id,long seed,Random r){
        Build b=new Build("multivariable",id,seed);int p=choose(seed,r,1,-3,3),q=choose(seed,r,-1,-3,3);if(p==0&&q==0)p=1;
        b.p("p",p);b.p("q",q);int f=p*p+p*q+q*q,fx=2*p+q,fy=p+2*q;need(f>0);
        String answer="z="+x(fx,"x")+add(fy,"y")+"-"+f;
        b.s("f_x=2x+y,\\quad f_y=x+2y","Compute the slope in each coordinate direction.")
         .s("f("+p+","+q+")="+f+",\\quad f_x("+p+","+q+")="+fx+",\\quad f_y("+p+","+q+")="+fy,"The base point must lie on the surface at its actual height.")
         .s("z="+f+"+("+fx+")(x-("+p+"))+("+fy+")(y-("+q+"))","Use the tangent-plane formula before collecting terms.")
         .s(answer,"Simplify the linear expression; substituting the base point recovers its height.")
         .h("Find f, fx and fy at the point.","Use z=f0+fx0(x-x0)+fy0(y-y0).","Check the plane passes through (x0,y0,f0).");
        return b.item("All real x,y; differentiable polynomial surface","z=x^2+xy+y^2,\\quad (x_0,y_0)=("+p+","+q+")","Find the tangent plane at the point with these x and y coordinates.",answer,null);
    }
    private static PracticeItem optimization(String id,long seed,Random r){
        Build b=new Build("optimization",id,seed);int a=b.p("a",choose(seed,r,3,2,5)),h=b.p("h",choose(seed,r,2,1,4)),radius=b.p("radius",choose(seed,r,3,2,5));need(a>0&&h>0&&radius>0);
        b.s("f(x,y)="+a+"x^2+(y-"+h+")^2-"+(h*h),"Complete the square in y. The quadratic terms are nonnegative.")
         .s("f(x,y)\\ge-"+(h*h)+",\\quad f(0,"+h+")=-"+(h*h),"Equality occurs only when both squares vanish, giving a global minimum.")
         .s("f(x,"+h+")="+a+"x^2-"+(h*h)+"\\longrightarrow+\\infty\\quad(|x|\\to\\infty)","The unconstrained function is unbounded above, so it has no global maximum.")
         .s("\\begin{cases}"+(2*a)+"x=2\\lambda x\\\\2y-"+(2*h)+"=2\\lambda y\\\\x^2+y^2="+(radius*radius)+"\\end{cases}","For the separate circle task, state gradient f=lambda gradient g with the constraint. The task asks for the system only; do not solve it.")
         .h("Complete the square in y to identify a lower bound.","Check whether the function grows without bound in some direction.","For the circle, write both gradient equations and the constraint; stop at the requested system.");
        return b.item("Unconstrained part: (x,y) in R^2. Separate constrained setup: circle radius "+radius+", with nonzero constraint gradient.","f(x,y)="+a+"x^2+y^2-"+(2*h)+"y","Find the global minimum and decide whether a global maximum exists on R^2. Separately, state ONLY the Lagrange system for x^2+y^2="+(radius*radius)+"; do not solve the constrained problem.","\\min f=-"+(h*h)+"\\text{ at }(0,"+h+");\\quad\\text{no global maximum on }\\mathbb R^2;\\quad\\begin{cases}"+(2*a)+"x=2\\lambda x\\\\2y-"+(2*h)+"=2\\lambda y\\\\x^2+y^2="+(radius*radius)+"\\end{cases}",null);
    }
    private static PracticeItem field(String id,long seed,Random r){
        Build b=new Build("vector-fields",id,seed);int c=b.p("c",choose(seed,r,2,1,5)),t=b.p("T",choose(seed,r,1,1,3));need(c>0&&t>0);
        int value=t*t*t*t+c*t*t*t;
        b.s("\\phi(x,y,z)=x^2y+"+c+"yz","Integrate the first component with respect to x, then choose the remaining function to match the other components.")
         .s("\\nabla\\phi=(2xy,x^2+"+c+"z,"+c+"y)=\\mathbf F","Verify all three partial derivatives. A global potential proves the field is conservative on R^3.")
         .s("\\mathbf r(0)=(0,0,0),\\quad\\mathbf r("+t+")=("+t+","+(t*t)+","+t+")","Evaluate the path endpoints, keeping the stated orientation.")
         .s("\\int_C\\mathbf F\\cdot d\\mathbf r=\\phi("+t+","+(t*t)+","+t+")-\\phi(0,0,0)="+value,"The fundamental theorem for line integrals reduces this to endpoint values.")
         .h("Try integrating 2xy with respect to x.","Match the y and z derivatives by adding c*y*z to the potential.","Use potential at the final endpoint minus potential at the initial endpoint.");
        return b.item("R^3; smooth field and path; 0<=t<="+t,"\\mathbf F=(2xy,x^2+"+c+"z,"+c+"y),\\quad\\mathbf r(t)=(t,t^2,t),\\quad0\\le t\\le"+t,"Find the work integral of F along the oriented path. Exhibit and verify a potential.",Integer.toString(value),(double)value);
    }
    private static PracticeItem mass(String id,long seed,Random r){
        Build b=new Build("double-integrals-mass",id,seed);int a=b.p("a",choose(seed,r,2,1,5));need(a>0);
        long a4=(long)a*a*a*a,a5=a4*a;String mass=frac(a4,12),moment=frac(a5,20),answer=frac(3*a,5);
        b.s("0\\le y\\le "+a+",\\quad0\\le x\\le "+a+"-y","Use horizontal slices of the triangular region.")
         .s("m=\\int_0^{"+a+"}\\int_0^{"+a+"-y}y^2\\,dx\\,dy=\\int_0^{"+a+"}y^2("+a+"-y)\\,dy="+mass,"Mass is density integrated over the region; it is positive.")
         .s("M_x=\\iint_R y\\rho\\,dA=\\int_0^{"+a+"}y^3("+a+"-y)\\,dy="+moment,"The first moment used for the y coordinate has an extra factor y. It is conventionally called the moment about the x-axis.")
         .s("\\bar y=\\frac{M_x}{m}="+answer,"Divide the first moment by the mass; the result lies between 0 and "+a+".")
         .h("A horizontal strip has width a-y.","Integrate y^2(a-y) for mass, then y^3(a-y) for the y first moment.","The center coordinate is the ratio of first moment to mass, not the moment alone.");
        return b.item("Triangle x>=0, y>=0, x+y<="+a+"; density y^2>=0; positive mass","R=\\{(x,y):x\\ge0,y\\ge0,x+y\\le"+a+"\\},\\quad\\rho(x,y)=y^2","Find the y-coordinate of the center of mass. Show the mass and the first moment used.","\\bar y="+answer,3*a/5.0);
    }
}
