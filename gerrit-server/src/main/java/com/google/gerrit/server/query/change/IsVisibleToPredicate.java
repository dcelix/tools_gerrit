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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.change.ChangeData.NeededData;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Provider;

import java.util.EnumSet;

class IsVisibleToPredicate extends OperatorPredicate<ChangeData> implements
    Prefetchable {
  private static String describe(CurrentUser user) {
    if (user instanceof IdentifiedUser) {
      return ((IdentifiedUser) user).getAccountId().toString();
    }
    if (user instanceof SingleGroupUser) {
      return "group:" + ((SingleGroupUser) user).getEffectiveGroups() //
          .iterator().next().toString();
    }
    return user.toString();
  }

  private final Provider<ReviewDb> db;
  private final CurrentUser user;
  private final ProjectCache projectCache;

  IsVisibleToPredicate(Provider<ReviewDb> db, ProjectCache projectCache,
      CurrentUser user) {
    super(ChangeQueryBuilder.FIELD_VISIBLETO, describe(user));
    this.db = db;
    this.projectCache = projectCache;
    this.user = user;
  }

  @Override
  public boolean match(final ChangeData cd) throws OrmException {
    if (cd.fastIsVisibleTo(user)) {
      return true;
    }
    Change c = cd.change(db);
    if (c != null
        && cd.projectState(db, projectCache).controlFor(user).controlFor(c).isVisible()) {
      cd.cacheVisibleTo(user);
      return true;
    } else {
      return false;
    }
  }

  @Override
  public int getCost() {
    return 1;
  }

  @Override
  public EnumSet<NeededData> getNeededData() {
    return EnumSet.of(NeededData.PROJECT_STATE, NeededData.CHANGE);
  }
}
