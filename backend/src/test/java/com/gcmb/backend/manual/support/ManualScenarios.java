/**
 * Copyright (c) 2026 SEP490-G12.
 *
 * Author: CaoNM
 * Created: 20/10/2026
 * Description: Groups local manual scenarios and prints observations for human verification.
 */

package com.gcmb.backend.manual.support;

import com.gcmb.backend.manual.ManualTestCase;
import com.gcmb.backend.manual.ManualTestResult;
import com.gcmb.backend.manual.ManualTestSuite;
import com.gcmb.backend.manual.ManualTestSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.gcmb.backend.manual.support.ManualTestData.*;
import static org.mockito.Mockito.mockStatic;

public abstract class ManualScenarios<F extends ManualFixture> {
    private final String module,sourceClass;
    private final Map<String,Case<F>> cases=new LinkedHashMap<>();

    private record Variant<F>(Consumer<F> setup,int status,String field,Consumer<F> inspect) {
    }
    private static final class Case<F> {
        final String id,subfeature,method,pre,input,expected;
        final int variantCount;
        final Supplier<F> fixture;
        final Function<F,Object> action;
        final List<Variant<F>> variants=new ArrayList<>();
        Case(String id,String subfeature,String method,String pre,String input,String expected,int count,Supplier<F> fixture,Function<F,Object> action) {
            this.id=id;
            this.subfeature=subfeature;
            this.method=method;
            this.pre=pre;
            this.input=input;
            this.expected=expected;
            variantCount=count;
            this.fixture=fixture;
            this.action=action;
        }
    }

    protected ManualScenarios(String module,String sourceClass) {
        this.module=module;
        this.sourceClass=sourceClass;
    }

    protected final void define(String id,String subfeature,String method,String pre,String input,String expected,int count,Supplier<F> fixture,Function<F,Object> action) {
        if(cases.putIfAbsent(id,new Case<>(id,subfeature,method,pre,input,expected,count,fixture,action))!=null)throw new IllegalStateException("Duplicate case "+id);
    }

    public final void add(String id,Consumer<F> setup,int status,String field,Consumer<F> inspect) {
        Case<F> test=cases.get(id);
        if(test==null)throw new IllegalArgumentException("Unknown case "+id);
        test.variants.add(new Variant<>(setup,status,field,inspect));
    }

    public final void add(String id,Consumer<F> setup,int status,String field) {
        add(id,setup,status,field,e-> {
        });
    }

    protected final void valid(String id,Consumer<F> setup,Consumer<F> inspect) {
        add(id,setup,0,null,inspect);
    }

    protected final void invalid(String id,String field,Object... values) {
        for(Object value:values)add(id,e-> {
            if(field.equals("$command"))e.cmd=value;
            else e.command(field,value);
        },400,null,ManualFixture::noWrites);
    }

    protected final void ok(String prefix,int n,Consumer<F> setup,Consumer<F> inspect) {
        valid(key(prefix,n),setup,inspect);
    }

    protected final void bad(String prefix,int n,String field,Object... values) {
        invalid(key(prefix,n),field,values);
    }

    protected final void bad(String id,Consumer<F> setup,int status,String field) {
        add(id,setup,status,field,ManualFixture::noWrites);
    }

    protected final void error(String prefix,int n,int status,Consumer<F> setup) {
        add(key(prefix,n),setup,status,null,e-> {
            if(status==400)e.noWrites();
        });
    }

    protected final List<ManualTestSuite> buildSuites() {
        Map<String,List<ManualTestCase>> groups=new LinkedHashMap<>();
        for(Case<F> test:cases.values()) {
            if(test.variants.size()!=test.variantCount)throw new IllegalStateException(test.id+": expected "+test.variantCount+" variants, found "+test.variants.size());
            groups.computeIfAbsent(test.subfeature,k->new ArrayList<>()).add(new ManualTestCase(
                "unit/"+test.id.toLowerCase(Locale.ROOT),test.id,sourceClass,test.method,test.pre,test.input,test.expected,()->observe(test)));
        }
        return groups.entrySet().stream().map(g->new ManualTestSuite(module,g.getKey(),g.getValue())).toList();
    }

    private ManualTestResult observe(Case<F> test) {
        StringBuilder actual=new StringBuilder(),exceptions=new StringBuilder(),effects=new StringBuilder();
        for(int i=0;i<test.variants.size();i++) {
            String label=String.format("%s / V%02d",test.id,i+1);
            Variant<F> variant=test.variants.get(i);
            actual.append("--- ").append(label).append(" ---\n");
            F fixture=null;
            boolean called=false;
            try {
                fixture=test.fixture.get();
                Map<String,String> defaults=new TreeMap<>();
                fixture.f.forEach((key,value)->defaults.put(key,String.valueOf(value)));
                variant.setup.accept(fixture);
                actual.append("Input: ").append(fixture.cmd).append("\nFixed time: ").append(fixture.now).append('\n');
                actual.append("Actor: ").append(fixture.actor).append('\n');
                Map<String,String> changes=new TreeMap<>();
                fixture.f.forEach((key,value)->{
                    if(!defaults.containsKey(key)||!Objects.equals(defaults.get(key),String.valueOf(value)))changes.put(key,String.valueOf(value));
                });
                if(!changes.isEmpty())actual.append("Changed fixture data: ").append(changes).append('\n');
                if(!fixture.answers.isEmpty())actual.append("Dependency overrides: ").append(fixture.answers).append('\n');
                actual.append("Expected outcome: ").append(switch(variant.status) {
                    case 0 -> "normal return";
                    case -2 -> "SQL callback exception";
                    case -3 -> "original constraint exception";
                    case -4 -> "ledger mismatch exception";
                    default -> "business error "+variant.status+(variant.field==null?"":"; field="+variant.field);
                }).append('\n');
                F e=fixture;
                Instant fixed=e.now;
                try(MockedStatic<Instant> clock=mockStatic(Instant.class,Mockito.CALLS_REAL_METHODS)) {
                    clock.when(Instant::now).thenReturn(fixed);
                    called=true;
                    try {
                        e.actual=test.action.apply(e);
                    }
                    catch(Throwable failure) {
                        e.failure=failure;
                    }
                }
                if(e.failure instanceof IllegalStateException && e.failure.getMessage()!=null && e.failure.getMessage().startsWith("Unarranged"))throw new ManualFixture.SetupUnavailable(e.failure.getMessage());
                actual.append("Return: ").append(e.actual).append('\n');
                exceptions.append(label).append(": ").append(ManualTestSupport.renderException(e.failure)).append('\n');
                try {
                    e.expect(variant.status,variant.field);
                    variant.inspect.accept(e);
                    actual.append("Observation checks: no difference detected; compare with Expected.\n");
                }
                catch(AssertionError difference) {
                    actual.append("Difference to inspect: ").append(difference.getMessage()).append('\n');
                }
            }
            catch(ManualFixture.SetupUnavailable unavailable) {
                actual.append(called?"Incomplete observation: ":"Business method NOT executed: ").append(unavailable.getMessage()).append('\n');
            }
            catch(Throwable setupFailure) {
                actual.append("TEST SETUP / OBSERVATION ERROR: ").append(ManualTestSupport.renderException(setupFailure)).append('\n');
                if(!called)actual.append("Business method NOT executed.\n");
            }
            if(fixture!=null)effects.append("--- ").append(label).append(" ---\n").append(fixture.invocations()).append('\n');
            actual.append('\n');
        }
        return new ManualTestResult(actual.toString(),exceptions.toString(),effects.toString());
    }
}
