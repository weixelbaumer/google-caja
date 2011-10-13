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

package com.google.caja.gwtbeans.shared;

import com.google.gwt.core.client.JavaScriptObject;

public class FloatTamingImpl implements FloatTaming {
  @Override
  public native JavaScriptObject getJso(Frame m, Float bean) /*-{
    return bean === null
        ? null
        : new Number(bean.@java.lang.Float::floatValue()());
  }-*/;

  @Override
  public native Float getBean(Frame m, JavaScriptObject jso) /*-{
    return jso === null
        ? null
        : @java.lang.Float::new(F)(Number(jso));
  }-*/;
}