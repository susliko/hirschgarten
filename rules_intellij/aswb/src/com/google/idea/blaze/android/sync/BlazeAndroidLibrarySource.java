/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.sync;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.android.sync.model.AarLibrary;
import com.google.idea.blaze.android.sync.model.BlazeAndroidSyncData;
import com.google.idea.blaze.base.ideinfo.ArtifactLocation;
import com.google.idea.blaze.base.model.BlazeLibrary;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.sync.libraries.LibrarySource;
import com.google.idea.blaze.java.sync.model.BlazeJarLibrary;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/** {@link LibrarySource} for android. */
public class BlazeAndroidLibrarySource extends LibrarySource.Adapter {
  private final BlazeProjectData blazeProjectData;

  BlazeAndroidLibrarySource(BlazeProjectData blazeProjectData) {
    this.blazeProjectData = blazeProjectData;
  }

  @Override
  public List<BlazeLibrary> getLibraries() {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<BlazeLibrary> libraries = ImmutableList.builder();
    if (syncData.importResult.resourceLibraries != null) {
      libraries.addAll(syncData.importResult.resourceLibraries.values());
    }
    for (BlazeJarLibrary javacJarLibrary : syncData.importResult.javacJarLibraries) {
      libraries.add(javacJarLibrary);
    }
    libraries.addAll(syncData.importResult.aarLibraries.values());
    return libraries.build();
  }

  @Nullable
  @Override
  public Predicate<BlazeLibrary> getLibraryFilter() {
    BlazeAndroidSyncData syncData = blazeProjectData.getSyncState().get(BlazeAndroidSyncData.class);
    if (syncData == null || syncData.importResult.aarLibraries.isEmpty()) {
      return null;
    }
    return new AarJarFilter(syncData.importResult.aarLibraries.values());
  }

  /**
   * Filters out any {@link BlazeJarLibrary} from the BlazeJavaWorkspaceImporter that are overridden
   * by an {@link AarLibrary} from the BlazeAndroidWorkspaceImporter.
   */
  @VisibleForTesting
  public static class AarJarFilter implements Predicate<BlazeLibrary> {

    private final Set<String> aarJarsPaths;

    public AarJarFilter(Collection<AarLibrary> aarLibraries) {
      Set<String> aarJarsPaths = new HashSet<>();
      for (AarLibrary aarLibrary : aarLibraries) {
        ArtifactLocation location = aarLibrary.libraryArtifact.jarForIntellijLibrary();
        // Keep track of the paths module the "configuration". It might be that we have a host
        // config (x86-64) and various target configurations (armv7a, aarch64).
        // In the TargetMap we pick one of the configuration targets. We then use the TargetMap
        // to figure out aar libraries. However, what we picked might not match up with jdeps, and
        // we'd end up creating a jar library from jdeps. Then when we compare the aar
        // against the jar library, it won't match unless we ignore the configuration segment.
        String configurationLessPath = location.getRelativePath();
        aarJarsPaths.add(configurationLessPath);
      }
      this.aarJarsPaths = aarJarsPaths;
    }

    @Override
    public boolean test(BlazeLibrary blazeLibrary) {
      if (!(blazeLibrary instanceof BlazeJarLibrary)) {
        return true;
      }
      BlazeJarLibrary jarLibrary = (BlazeJarLibrary) blazeLibrary;
      ArtifactLocation location = jarLibrary.libraryArtifact.jarForIntellijLibrary();
      String configurationLessPath = location.getRelativePath();
      return !aarJarsPaths.contains(configurationLessPath);
    }
  }
}
