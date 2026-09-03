package com.n0hana.echoes_server.service.validation;

import org.springframework.beans.BeanWrapperImpl;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordValueMatchValidator implements ConstraintValidator<PasswordValueMatch, Object>{

    
    private String field;
    private String fieldMatch;


    @Override
    public void initialize(PasswordValueMatch constraintAnnotation) {
        this.field = constraintAnnotation.field();
        this.fieldMatch = constraintAnnotation.fieldMatch();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        
        Object fieldValue = new BeanWrapperImpl(value).getPropertyValue(field);
        Object fieldMatchValue = new BeanWrapperImpl(value).getPropertyValue(fieldMatch);

        if (fieldValue != null) return fieldValue.equals(fieldMatchValue);
        else return fieldMatchValue == null;
        
    }
    
    
}
