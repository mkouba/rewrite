/*
 * Copyright 2011 <a href="mailto:lincolnbaxter@gmail.com">Lincoln Baxter, III</a>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ocpsoft.rewrite.annotation.handler;

import java.lang.reflect.Field;

import org.ocpsoft.logging.Logger;
import org.ocpsoft.rewrite.annotation.ParameterBinding;
import org.ocpsoft.rewrite.annotation.api.FieldContext;
import org.ocpsoft.rewrite.annotation.api.HandlerChain;
import org.ocpsoft.rewrite.annotation.spi.FieldAnnotationHandler;
import org.ocpsoft.rewrite.bind.BindingBuilder;
import org.ocpsoft.rewrite.config.Condition;
import org.ocpsoft.rewrite.config.Visitor;
import org.ocpsoft.rewrite.el.El;
import org.ocpsoft.rewrite.param.Parameter;
import org.ocpsoft.rewrite.param.Parameterized;

public class ParameterBindingHandler extends FieldAnnotationHandler<ParameterBinding>
{

   private final Logger log = Logger.getLogger(ParameterBindingHandler.class);

   @Override
   public Class<ParameterBinding> handles()
   {
      return ParameterBinding.class;
   }

   @Override
   public int priority()
   {
      return HandlerWeights.WEIGHT_TYPE_STRUCTURAL;
   }

   @Override
   public void process(FieldContext context, ParameterBinding annotation, HandlerChain chain)
   {

      // default name is the name of the field
      Field field = context.getJavaField();
      String param = field.getName();

      // but the name specified in the annotation is preferred
      if (!annotation.value().isEmpty()) {
         param = annotation.value().trim();
      }

      if (log.isTraceEnabled()) {
         log.trace("Binding parameter [{}] to field [{}]", param, field);
      }

      // add bindings to conditions by walking over the condition tree
      context.getRuleBuilder().accept(new AddBindingVisitor(context, param, field));

      // continue
      chain.proceed();

   }

   /**
    * Visitor to add
    */
   private static class AddBindingVisitor implements Visitor<Condition>
   {

      private final Logger log = Logger.getLogger(AddBindingVisitor.class);

      private final String param;
      private final FieldContext context;
      private final Field field;

      public AddBindingVisitor(FieldContext context, String paramName, Field field)
      {
         this.context = context;
         this.param = paramName;
         this.field = field;
      }

      @Override
      @SuppressWarnings("rawtypes")
      public void visit(Condition condition)
      {

         // only conditions with parameters interesting
         if (condition instanceof Parameterized) {
            Parameterized parameterized = (Parameterized) condition;

            // The binding to add
            El elBinding = El.property(field);

            // the parameter may not exist in the Parameterized instance
            try {

               // add the parameter and the binding
               Parameter parameter = parameterized.where(param);

               // other handlers will use the context to enrich the parameter
               context.put(Parameter.class, parameter);

               // the binding may also be enriched
               parameter.bindsTo(elBinding);
               context.put(BindingBuilder.class, elBinding);

               if (log.isDebugEnabled()) {
                  log.debug("Added binding for parameter [{}] to: {}", param, parameterized.getClass().getSimpleName());
               }

            }

            // parameter does not exist
            catch (IllegalArgumentException e) {
               if (log.isTraceEnabled()) {
                  log.trace("Parameter [{}] not found on: {}", param, parameterized.getClass().getSimpleName());
               }
            }

         }
      }
   }

}