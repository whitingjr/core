/*
 * JBoss, Home of Professional Open Source
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * by the @authors tag. See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.webbeans.bean.proxy;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;

import javassist.util.proxy.MethodHandler;

import javax.enterprise.context.spi.CreationalContext;

import org.jboss.webbeans.bean.SessionBean;
import org.jboss.webbeans.ejb.api.SessionObjectReference;
import org.jboss.webbeans.introspector.MethodSignature;
import org.jboss.webbeans.introspector.jlr.MethodSignatureImpl;
import org.jboss.webbeans.log.Log;
import org.jboss.webbeans.log.Logging;
import org.jboss.webbeans.util.Reflections;

/**
 * Method handler for enterprise bean client proxies
 * 
 * @author Nicklas Karlsson
 * @author Pete Muir
 * 
 */
public class EnterpriseBeanProxyMethodHandler<T> implements MethodHandler, Serializable
{

   private static final long serialVersionUID = 2107723373882153667L;

   // The log provider
   static final transient Log log = Logging.getLog(EnterpriseBeanProxyMethodHandler.class);

   private final SessionObjectReference reference; 
   private final Class<?> objectInterface;
   private final Collection<MethodSignature> removeMethodSignatures;
   private final boolean clientCanCallRemoveMethods;

   /**
    * Constructor
    * 
    * @param removeMethods
    * 
    * @param proxy The generic proxy
    */
   public EnterpriseBeanProxyMethodHandler(SessionBean<T> bean, CreationalContext<T> creationalContext)
   {
      this.objectInterface = bean.getEjbDescriptor().getObjectInterface();
      this.removeMethodSignatures = bean.getEjbDescriptor().getRemoveMethodSignatures();
      this.clientCanCallRemoveMethods = bean.isClientCanCallRemoveMethods();
      this.reference = bean.createReference();
      log.trace("Created enterprise bean proxy method handler for " + bean);
   }
   
   /**
    * Lookups the EJB in the container and executes the method on it
    * 
    * @param self the proxy instance.
    * @param method the overridden method declared in the super class or
    *           interface.
    * @param proceed the forwarder method for invoking the overridden method. It
    *           is null if the overridden method is abstract or declared in the
    *           interface.
    * @param args an array of objects containing the values of the arguments
    *           passed in the method invocation on the proxy instance. If a
    *           parameter type is a primitive type, the type of the array
    *           element is a wrapper class.
    * @return the resulting value of the method invocation.
    * 
    * @throws Throwable if the method invocation fails.
    */
   public Object invoke(Object self, Method method, Method proceed, Object[] args) throws Throwable
   {
      if (reference.isRemoved())
      {
         return null;
      }
      if ("destroy".equals(method.getName()) && Marker.isMarker(0, method, args))
      {
         reference.remove();
         return null;
      }
      
      if (!clientCanCallRemoveMethods)
      {
         // TODO we can certainly optimize this search algorithm!
         MethodSignature methodSignature = new MethodSignatureImpl(method);
         if (removeMethodSignatures.contains(methodSignature))
         {
            throw new UnsupportedOperationException("Cannot call EJB remove method directly on non-dependent scoped bean " + method );
         }
      }
      Class<?> businessInterface = getBusinessInterface(method);
      Object proxiedInstance = reference.getBusinessObject(businessInterface);
      Method proxiedMethod = Reflections.lookupMethod(method, proxiedInstance);
      Object returnValue = Reflections.invokeAndWrap(proxiedMethod, proxiedInstance, args);
      log.trace("Executed " + method + " on " + proxiedInstance + " with parameters " + args + " and got return value " + returnValue);
      return returnValue;
   }
   
   private Class<?> getBusinessInterface(Method method)
   {
      Class<?> businessInterface = method.getDeclaringClass();
      if (businessInterface.equals(Object.class))
      {
         return objectInterface;
      }
      else
      {
         return businessInterface;
      }
   }
   
}
