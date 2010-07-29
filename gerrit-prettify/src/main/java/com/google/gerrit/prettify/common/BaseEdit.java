// Copyright (C) 2010 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.prettify.common;

import com.google.gwtorm.client.Column;

import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.diff.Edit.Type;

public class BaseEdit {
  @Column(id = 1)
  protected int beginA;

  @Column(id = 2)
  protected int endA;

  @Column(id = 3)
  protected int beginB;

  @Column(id = 4)
  protected int endB;

  protected BaseEdit() {
  }

  public BaseEdit(int beginA, int endA, int beginB, int endB) {
    this.beginA = beginA;
    this.endA = endA;
    this.beginB = beginB;
    this.endB = endB;
  }

  public BaseEdit(Edit edit) {
    this(edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB());
  }

  public int getBeginA() {
    return beginA;
  }

  public int getEndA() {
    return endA;
  }

  public int getBeginB() {
    return beginB;
  }

  public int getEndB() {
    return endB;
  }

  public final Type getType() {
    if (beginA == endA) {
      if (beginB < endB) {
        return Type.INSERT;
      }
      if (beginB == endB) {
        return Type.EMPTY;
      }
    }
    if (beginA < endA && beginB == endB) {
      return Type.DELETE;
    }
    return Type.REPLACE;
  }
}
