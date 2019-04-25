/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.command.buildresult;

import com.google.devtools.intellij.model.ProjectData;
import com.google.idea.blaze.base.filecache.ArtifactState;
import com.google.idea.blaze.base.filecache.ArtifactState.GenericOutputState;
import com.google.idea.blaze.base.ideinfo.ProtoWrapper;
import com.intellij.openapi.extensions.ExtensionPointName;
import java.util.Arrays;
import java.util.Objects;
import javax.annotation.Nullable;

/** A blaze output artifact which is hosted by some remote service. */
public interface RemoteOutputArtifact
    extends OutputArtifact, ProtoWrapper<ProjectData.OutputArtifact> {

  @Nullable
  static RemoteOutputArtifact fromProto(ProjectData.OutputArtifact proto) {
    return Arrays.stream(Parser.EP_NAME.getExtensions())
        .map(p -> p.parseProto(proto))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  default ArtifactState toArtifactState() {
    return new GenericOutputState(getRelativePath(), getHashId(), getSyncTimeMillis());
  }

  @Override
  default ProjectData.OutputArtifact toProto() {
    return ProjectData.OutputArtifact.newBuilder()
        .setRelativePath(getRelativePath())
        .setId(getHashId())
        .setSyncStartTimeMillis(getSyncTimeMillis())
        .build();
  }

  @Override
  default String getKey() {
    return getRelativePath();
  }

  /** The blaze-out-relative path. */
  String getRelativePath();

  /** Reads the artifact contents into memory, using a soft reference. */
  void prefetch();

  /**
   * A string uniquely identifying this artifact. Instances of this artifact with different contents
   * will have different IDs.
   *
   * <p>May be used to retrieve it from a remote caching service.
   */
  String getHashId();

  long getSyncTimeMillis();

  /** Converts ProjectData.OutputArtifact to RemoteOutputArtifact. */
  interface Parser {
    ExtensionPointName<Parser> EP_NAME =
        ExtensionPointName.create("com.google.idea.blaze.RemoteOutputArtifactParser");

    @Nullable
    RemoteOutputArtifact parseProto(ProjectData.OutputArtifact proto);
  }
}
