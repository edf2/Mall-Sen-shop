package com.mallsen.aspect;

import com.mallsen.annotation.AutoFill;
import com.mallsen.constant.AutoFillConstant;
import com.mallsen.context.BaseContext;
import com.mallsen.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面 实现公共字段自动填充处理
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点定义
    * 该方法定义了一个AOP切入点，用于匹配特定条件的方法执行
    * 当满足以下两个条件时，切入点会匹配到目标方法：
    * 1. 方法执行路径在com.mallsen.mapper包下的任意类的任意方法
    * 2. 方法上带有@com.mallsen.annotation.AutoFill注解
     */
    @Pointcut("execution(* com.mallsen.mapper.*.*(..)) && @annotation(com.mallsen.annotation.AutoFill)")
    public void autoFillPointCut() {
    // 这是一个空方法体，仅用于定义切入点
    // 方法名autoFillPointCut作为切入点标识符，可在其他通知中引用
    }

    /**
     * 前置通知，在通知中进行公共直段的赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        // 在这里编写公共字段赋值逻辑
        // 可以通过joinPoint获取目标方法的信息，如参数、注解等
        // 然后根据需要自动填充公共字段
        log.info("开始自动填充公共字段...");

        //获取到当前被拦截的方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();//获取到被拦截的方法签名
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);//获取到被拦截的方法上的注解
        OperationType operationType = autoFill.value();//获取到被拦截的方法上的注解的值即数据库的操作类型

        //获取到当前被拦截的方法上的参数
        Object[] args = joinPoint.getArgs();//获取到被拦截的方法的参数
        if (args == null || args.length == 0) {
            return;
        }

        Object entity = args[0];

        //准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentid = BaseContext.getCurrentId();

        //根据不同操作类型，为对应的属性通过反射来赋值
        if (operationType == OperationType.INSERT) {
            //给4个公共字段赋值
            try {
                Method setCreateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class);
                Method setCreateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_CREATE_USER, Long.class);
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                //通过反射为对象属性赋值
                setCreateTime.invoke(entity, now);
                setCreateUser.invoke(entity, currentid);
                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentid);

            } catch (Exception e) {
                log.error("自动填充INSERT公共字段失败，entity: {}", entity, e);
            }

        } else if (operationType == OperationType.UPDATE) {
            //给2个公共字段赋值
            try {
                Method setUpdateTime = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class);
                Method setUpdateUser = entity.getClass().getDeclaredMethod(AutoFillConstant.SET_UPDATE_USER, Long.class);

                setUpdateTime.invoke(entity, now);
                setUpdateUser.invoke(entity, currentid);
            } catch (Exception e) {
                log.error("自动填充UPDATE公共字段失败，entity: {}", entity, e);
            }


        }

    }
}
