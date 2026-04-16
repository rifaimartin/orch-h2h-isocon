FROM gitlab.dgcpdev.com:5050/framework/middleware/openjdk:21

# Install troubleshooting tools (nmap-ncat for nc, telnet)
#RUN microdnf install -y nmap-ncat telnet && \
#     microdnf clean all

# Create a non-root user and group
RUN groupadd --system --gid 995 appframeworkgroup && \
    useradd --system --create-home --uid 995 --gid appframeworkgroup appframework

WORKDIR /orch/h2h-isocon

# Copy JAR file (built separately by CI/CD)
COPY ./h2h-iso8583-bcad-1.0.0-SNAPSHOT.jar /orch/h2h-isocon/h2h-iso8583-bcad-1.0.0-SNAPSHOT.jar

# Set ownership
RUN chown -R appframework:appframeworkgroup /orch/h2h-isocon

# Switch to non-root user
USER appframework

ENV TZ="Asia/Jakarta"
RUN date

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "h2h-iso8583-bcad-1.0.0-SNAPSHOT.jar"]