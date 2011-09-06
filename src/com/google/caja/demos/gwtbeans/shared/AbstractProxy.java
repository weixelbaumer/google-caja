// Copyright (C) 2011 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.caja.demos.gwtbeans.shared;

import java.util.ArrayList;
import java.util.List;

import com.google.gwt.core.client.JavaScriptObject;

public abstract class AbstractProxy<BeanType> implements Proxy<BeanType> {
  @Override
  public JavaScriptObject getJso(Frame mi, BeanType bean) {
    FrameImpl m = (FrameImpl) mi;
    JavaScriptObject jso;
    if (m.getJsoByBean().containsKey(bean)) {
      jso = m.getJsoByBean().get(bean);
      // GWT static compilation should ensure that we never return the wrong
      // type of object, but it's good to check anyway in case of coding errors
      if (!m.getClassesByJso().get(jso).contains(getBeanClassName())) {
        throw new Error("Internal error: Bean -> JSO map encountered type unsafe condition");        
      }
    } else {
      jso = getNative(m, bean);    
      m.getJsoByBean().put(bean, jso);
      m.getBeanByJso().put(jso, bean);
      m.getClassesByJso().put(jso, getClasses(bean));
    }
    return jso;
  }

  @Override
  public BeanType getBean(Frame frame, JavaScriptObject jso) {
    FrameImpl m = (FrameImpl) frame;    
    Object bean = m.getBeanByJso().get(jso);
    if (bean == null) { throw new RuntimeException("Cannot find bean for given JSO " + jso); }
    if (!m.getClassesByJso().get(jso).contains(getBeanClassName())) {
      throw new RuntimeException("Invalid cast from " + m.getClassesByJso().get(jso) + " to " + getBeanClassName());
    }
    return castToBeanType(bean);
  }
  
  @SuppressWarnings("unchecked")
  private BeanType castToBeanType(Object bean) {
    return (BeanType) bean;    
  }
  
  protected abstract JavaScriptObject getNative(Frame m, BeanType bean);
  
  protected abstract String getBeanClassName();
  
  private List<String> getClasses(BeanType bean) {
    List<String> result = new ArrayList<String>();
    for (Class<?> clazz = bean.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
      // TODO(ihab.awad): This adds "class com.foo.Bar" instead of just "com.foo.Bar". But the
      // GWT implementation of Class does not include getCanonialName(). Find a cleaner solution
      // and remember to change corresponding getBeanClassName() generated by ProxyGenerator.
      result.add(clazz.toString());
    }
    return result;
  }
}
