FROM mongo
COPY data-import/ /data-import/
RUN chmod +x /data-import/import.sh
EXPOSE 27016
ENTRYPOINT /data-import/import.sh
