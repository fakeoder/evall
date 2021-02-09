package com.fakeoder.condeval.core;

import com.fakeoder.condeval.exception.ExpressionException;

import java.util.*;

/**
 * @author zhuo
 */
public class Expression {

    private Map<String,Object> context;

    /**
     * operator stack
     */
    private Stack<IOperator> operatorStack = new CustomStack("OPERATOR");

    /**
     * operator number stack
     */
    private Stack<String> variableStack = new CustomStack("VARIABLE");


    /**
     * finally result
     */
    private Object result;

    /**
     * load express from expression string
     * @param expressionStr
     * @return
     */
    public static Object eval(String expressionStr, Map<String,Object> context){
        if(expressionStr==null||expressionStr.isEmpty()){
            throw new ExpressionException("expression is empty!");
        }

        Expression expression = new Expression(context);
        //todo valid this expression

        char[] characters = expressionStr.toCharArray();
        String storage = "";
        boolean preIsOperator = false;
        boolean hasLeft = true;
        Operator preOperator = null;
        int idx = 0;
        for(; idx < characters.length; idx++){
            if(preIsOperator){
                if(Operator.maybeOperator(storage+characters[idx])) {
                    Operator operator = Operator.valueOfSymbol(storage + characters[idx]);
                    if (operator == null) {
                        if (preOperator != null) {
                            idx = expression.pushOperator(preOperator.getSymbol(), characters, idx);
                            preOperator = null;
                            storage = characters[idx] + "";
                        } else {
                            storage += characters[idx];
                        }
                    } else {
                        preOperator = operator;
                        storage += characters[idx];
                    }
                    preIsOperator = true;
                }else{
                    idx = expression.pushOperator(storage, characters, idx);
                    if(idx>characters.length-1){
                        hasLeft = false;
                        break;
                    }
                    preOperator = null;
                    storage = characters[idx] + "";
                    preIsOperator = Operator.maybeOperator(characters[idx]+"");
                }
            }else {
                if(Operator.maybeOperator(characters[idx]+"")) {
                    //not first time
                    if(!storage.isEmpty()){
                        expression.pushVariable(storage);
                    }
                    storage = characters[idx]+"";
                    preIsOperator = true;
                }else{
                    storage += characters[idx];
                    preIsOperator = false;
                }
            }
        }

        if(hasLeft) {
            if (!preIsOperator) {
                expression.pushVariable(storage);
            } else {
                //must be right bracket
                expression.rightBracketDeal();
            }
        }


        while(!expression.operatorStack.isEmpty()) {
            IOperator operator = expression.operatorStack.pop();
            expression.operatorEval(operator);
        }
        return expression.variableStack.pop();
    }

    /**
     *
     * @param variable
     */
    private void pushVariable(String variable) {
        variableStack.push(variable);
    }

    /**
     *
     * @param params
     */
    private void pushVariables(List<String> params) {
        for (String param : params) {
            pushVariable(param);
        }
    }

    /**
     * eval and push
     * @param operatorStr
     * @param characters
     * @param idx the first param parameter index
     * @return
     */
    private int pushOperator(String operatorStr, char[] characters, int idx) {
        //valid
        Operator operator = Operator.valueOfSymbol(operatorStr);
        if(operator==null){
            throw new ExpressionException(String.format("not valid operator:%s", operatorStr));
        }

        if(operatorStack.isEmpty()){
            operatorStack.push(operator);
            List<String> params = new ArrayList<>();
            //todo this is important, need check.
            idx = operator.findParameter(characters,idx, params);
            pushVariables(params);
        }else {
            IOperator operatorPre = operatorStack.peek();
            if (operator.lessThan(operatorPre)) {
                if(operator!=Operator.BRACKET_LEFT) {
                    operatorStack.pop();
                    operatorEval(operatorPre);
                }
                operatorStack.push(operator);
            }else {
                if(operator==Operator.BRACKET_RIGHT){
                    rightBracketDeal();
                }else {
                    List<String> params = new ArrayList<>();
                    if(operator.isNeedPush()) {
                        operatorStack.push(operator);
                    }
                    //todo this is important, need check.
                    idx = operator.findParameter(characters, idx, params);
                    pushVariables(params);
                }
            }
        }
        //return next index
        return idx;
    }

    /**
     * visit right bracket, should back, eval and push value
     */
    public void rightBracketDeal(){
        IOperator backOperator = null;
        while((backOperator=operatorStack.pop())!=Operator.BRACKET_LEFT) {
            String[] params = new String[backOperator.getParamSize()];
            for (int paramIdx = backOperator.getParamSize() - 1; paramIdx >= 0; paramIdx--) {
                params[paramIdx] = variableStack.pop();
            }
            Object result = backOperator.evalAround(params);
            pushVariable(result.toString());
        }
    }

    /**
     * eval operator
     * @param operator
     */
    public void operatorEval(IOperator operator){
        String[] params = new String[operator.getParamSize()];
        for (int paramIdx = operator.getParamSize() - 1; paramIdx >= 0; paramIdx--) {
            String val = variableStack.pop();
            //todo variables?(${name},"abc",123):expression(a+1)
            if(Variable.isVariable(val)) {
                params[paramIdx] = Variable.realVariable(val,context);
            }else{
                Object res = Expression.eval(val,context);
                params[paramIdx] = res==null?"":res.toString();
            }

        }
        //todo check logic
        Object result = operator.evalAround(params);
        pushVariable(result.toString());
    }


    /**
     * private
     */
    private Expression(Map<String,Object> context) {
        this.context = context;
    }


}
