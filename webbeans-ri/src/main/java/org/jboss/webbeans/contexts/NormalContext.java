package org.jboss.webbeans.contexts;

import java.lang.annotation.Annotation;

public abstract class NormalContext extends AbstractContext
{

   public NormalContext(Class<? extends Annotation> scopeType)
   {
      super(scopeType);
   }

}
