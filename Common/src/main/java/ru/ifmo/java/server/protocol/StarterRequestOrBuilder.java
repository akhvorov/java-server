// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: protocol.proto

package ru.ifmo.java.server.protocol;

public interface StarterRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:ru.ifmo.java.server.protocol.StarterRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string serverType = 1;</code>
   * @return The serverType.
   */
  java.lang.String getServerType();
  /**
   * <code>string serverType = 1;</code>
   * @return The bytes for serverType.
   */
  com.google.protobuf.ByteString
      getServerTypeBytes();

  /**
   * <pre>
   *    int32 requestsCount = 2;
   * </pre>
   *
   * <code>bool start = 2;</code>
   * @return The start.
   */
  boolean getStart();
}
