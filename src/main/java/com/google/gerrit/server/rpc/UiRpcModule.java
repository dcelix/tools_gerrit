// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.rpc;

import com.google.gerrit.server.http.RpcServletModule;
import com.google.gerrit.server.patch.PatchModule;
import com.google.gerrit.server.rpc.changedetail.ChangeModule;
import com.google.gerrit.server.rpc.project.ProjectModule;

/** Registers servlets to answer RPCs from client UI. */
public class UiRpcModule extends RpcServletModule {
  public static final String PREFIX = "/gerrit/rpc/";

  public UiRpcModule() {
    super(PREFIX);
  }

  @Override
  protected void configureServlets() {
    rpc(AccountServiceImpl.class);
    rpc(AccountSecurityImpl.class);
    rpc(GroupAdminServiceImpl.class);
    rpc(ChangeListServiceImpl.class);
    rpc(SuggestServiceImpl.class);
    rpc(SystemInfoServiceImpl.class);

    install(new ChangeModule());
    install(new PatchModule());
    install(new ProjectModule());
  }
}